import 'jsr:@supabase/supabase-js@2';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const body = await req.json();
    const {
      memberId,
      memberName,
      amount,
      phoneNumber,
      cycleMonthIndex,
      cycleYear,
      externalId,
      receiptImageUrl,
      notes,
      contributionType = 'SAVINGS',
    } = body;

    if (!memberId || !memberName || !amount || !phoneNumber) {
      return new Response(
        JSON.stringify({ success: false, message: 'memberId, memberName, amount and phoneNumber are required.' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const iotecBaseUrl = Deno.env.get('IOTEC_BASE_URL') || 'https://pay.iotec.io';
    const clientId = Deno.env.get('IOTEC_CLIENT_ID');
    const clientSecret = Deno.env.get('IOTEC_CLIENT_SECRET');
    const walletId = Deno.env.get('IOTEC_WALLET_ID');

    if (!clientId || !clientSecret || !walletId) {
      return new Response(
        JSON.stringify({ success: false, message: 'IOTEC_CLIENT_ID, IOTEC_CLIENT_SECRET and IOTEC_WALLET_ID are not configured.' }),
        { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const tokenForm = new URLSearchParams();
    tokenForm.set('client_id', clientId);
    tokenForm.set('client_secret', clientSecret);
    tokenForm.set('grant_type', 'client_credentials');

    const tokenResp = await fetch(`${iotecBaseUrl}/connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: tokenForm,
    });

    const tokenJson = await tokenResp.json();
    if (!tokenResp.ok || !tokenJson.access_token) {
      console.error('Iotec token error:', tokenJson);
      return new Response(
        JSON.stringify({ success: false, message: 'Failed to obtain iotec access token.', details: tokenJson }),
        { status: 502, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const accessToken = tokenJson.access_token as string;
    const normalizedPhone = formatMsisdn(phoneNumber);

    const collectBody = {
      category: 'MobileMoney',
      currency: 'UGX',
      walletId,
      externalId: externalId ?? `sacco-${memberId}-${Date.now()}`,
      payer: normalizedPhone,
      amount,
      payerNote: `SACCO contribution for ${memberName}`,
      payeeNote: notes ?? `Member: ${memberName} | Type: ${contributionType} | Month: ${cycleMonthIndex ?? ''} Year: ${cycleYear ?? ''}`,
    };

    const collectResp = await fetch(`${iotecBaseUrl}/api/collections/collect`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(collectBody),
    });

    const collectJson = await collectResp.json();
    if (!collectResp.ok || collectJson.id == null) {
      console.error('Iotec collect error:', collectJson);
      return new Response(
        JSON.stringify({ success: false, message: 'Iotec collection request failed.', details: collectJson }),
        { status: 502, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const supabase = createClient<Database>(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    );

    // Long polling configuration
    const maxPollingAttempts = 30; // 30 attempts
    const pollingIntervalSeconds = 10; // 10 seconds between polls
    const maxPollingDurationMs = maxPollingAttempts * pollingIntervalSeconds * 1000;

    // Store initial payment record
    await supabase.from('savings_payments').upsert({
      member_id: memberId,
      member_name: memberName,
      cycle_month_index: cycleMonthIndex ?? new Date().getMonth() + 1,
      cycle_year: cycleYear ?? new Date().getFullYear(),
      amount_paid: amount,
      remaining_balance: 0,
      date_paid: new Date().toISOString(),
      status: 'PENDING',
      receipt_number: collectJson.id,
      transaction_id: collectJson.id,
      bank_name: 'IOTEC',
      notes: notes ?? 'Pending iotec collection verification',
      _iotec_request_id: collectJson.id,
      _iotec_status: collectJson.status ?? 'Pending',
      _iotec_contribution_type: contributionType,
      _iotec_polling_started_at: new Date().toISOString(),
      _iotec_polling_attempts: 0,
    }, { onConflict: 'transaction_id' });

    // Start long polling in background
    const pollingResult = await pollIotecStatus(
      supabase,
      accessToken,
      iotecBaseUrl,
      collectJson.id,
      null,
      maxPollingAttempts,
      pollingIntervalSeconds
    );

    // Update final status
    const finalStatus = pollingResult.status;
    const mappedStatus = mapIotecStatus(finalStatus);

    await supabase.from('savings_payments').update({
      _iotec_status: finalStatus,
      status: mappedStatus,
      _iotec_polling_completed_at: new Date().toISOString(),
      _iotec_polling_attempts: pollingResult.attempts,
    }).eq('_iotec_request_id', collectJson.id);

    return new Response(
      JSON.stringify({
        success: true,
        requestId: collectJson.id,
        status: finalStatus,
        externalId: collectJson.externalId,
        amount,
        currency: collectJson.currency ?? 'UGX',
        receiptImageUrl,
        contributionType,
        polling: true,
        pollingCompleted: true,
        attempts: pollingResult.attempts,
      }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    );
  } catch (error) {
    console.error('Iotec collect function error:', error);
    return new Response(
      JSON.stringify({ success: false, message: error instanceof Error ? error.message : 'Unknown error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    );
  }
});

function formatMsisdn(phone: string): string {
  const digits = phone.replace(/[^0-9]/g, '');
  if (digits.startsWith('256')) {
    return digits;
  }
  if (digits.startsWith('0')) {
    return `256${digits.substring(1)}`;
  }
  return digits;
}

interface PollingResult {
  status: string;
  attempts: number;
}

async function pollIotecStatus(
  supabase: any,
  accessToken: string,
  iotecBaseUrl: string,
  requestId: string | null,
  externalId: string | null,
  maxAttempts: number,
  intervalSeconds: number
): Promise<PollingResult> {
  const statusQuery = requestId
    ? `/api/collections/status/${encodeURIComponent(requestId)}`
    : `/api/collections/external-id/${encodeURIComponent(externalId as string)}`;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const statusResp = await fetch(`${iotecBaseUrl}${statusQuery}`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });

      const statusJson = await statusResp.json();
      console.log(`Iotec poll attempt ${attempt}/${maxAttempts}:`, statusJson.status);

      if (!statusResp.ok) {
        console.error(`Poll attempt ${attempt} failed:`, statusJson);
        await new Promise((resolve) => setTimeout(resolve, intervalSeconds * 1000));
        continue;
      }

      // Update polling attempt count
      await supabase.from('savings_payments').update({
        _iotec_status: statusJson.status,
        _iotec_polling_attempts: attempt,
      }).eq('_iotec_request_id', requestId);

      // Check if payment is final
      const lowerStatus = (statusJson.status || '').toLowerCase();
      if (
        lowerStatus === 'success' ||
        lowerStatus === 'failed' ||
        lowerStatus === 'cancelled'
      ) {
        return { status: statusJson.status, attempts: attempt };
      }

      // Wait before next poll
      if (attempt < maxAttempts) {
        await new Promise((resolve) => setTimeout(resolve, intervalSeconds * 1000));
      }
    } catch (error) {
      console.error(`Poll attempt ${attempt} error:`, error);
      await new Promise((resolve) => setTimeout(resolve, intervalSeconds * 1000));
    }
  }

  // If we reach here, polling timed out
  return { status: 'TIMEOUT', attempts: maxAttempts };
}

function mapIotecStatus(iotecStatus: string | undefined): string | undefined {
  if (!iotecStatus) return undefined;

  switch (iotecStatus.toLowerCase()) {
    case 'success':
      return 'APPROVED';
    case 'failed':
      return 'REJECTED';
    case 'pending':
    case 'senttovendor':
      return 'PENDING';
    default:
      return 'PENDING';
  }
}
