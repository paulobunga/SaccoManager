import 'jsr:@supabase/supabase-js@2';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS, GET',
};

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const url = new URL(req.url);
    const requestId = url.searchParams.get('requestId');
    const externalId = url.searchParams.get('externalId');

    if (!requestId && !externalId) {
      return new Response(
        JSON.stringify({ success: false, message: 'requestId or externalId is required.' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const iotecBaseUrl = Deno.env.get('IOTEC_BASE_URL') || 'https://pay.iotec.io';
    const clientId = Deno.env.get('IOTEC_CLIENT_ID');
    const clientSecret = Deno.env.get('IOTEC_CLIENT_SECRET');

    if (!clientId || !clientSecret) {
      return new Response(
        JSON.stringify({ success: false, message: 'IOTEC credentials are not configured.' }),
        { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    // Get iotec access token
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

    // Determine which endpoint to use
    let statusUrl: string;
    if (requestId) {
      statusUrl = `${iotecBaseUrl}/api/collections/status/${encodeURIComponent(requestId)}`;
    } else {
      statusUrl = `${iotecBaseUrl}/api/collections/external-id/${encodeURIComponent(externalId as string)}`;
    }

    const statusResp = await fetch(statusUrl, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    const statusJson = await statusResp.json();
    if (!statusResp.ok) {
      console.error('Iotec status error:', statusJson);
      return new Response(
        JSON.stringify({ success: false, message: 'Failed to get iotec transaction status.', details: statusJson }),
        { status: 502, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    // If we have a Supabase instance, update the local record with latest status
    const supabaseUrl = Deno.env.get('SUPABASE_URL');
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
    
    if (supabaseUrl && serviceRoleKey) {
      const supabase = createClient<Database>(supabaseUrl, serviceRoleKey);
      
      // Build the update payload
      const updateData: Record<string, any> = {};
      
      if (statusJson.status) {
        updateData._iotec_status = statusJson.status;
      }
      
      // Map iotec status to our local status
      const mappedStatus = mapIotecStatus(statusJson.status);
      if (mappedStatus) {
        updateData.status = mappedStatus;
      }
      
      // Update the record if we found one
      if (Object.keys(updateData).length > 0) {
        let query = supabase.from('savings_payments');
        
        if (requestId) {
          query = query.update(updateData).eq('_iotec_request_id', requestId);
        } else if (externalId) {
          query = query.update(updateData).eq('transaction_id', externalId);
        }
        
        await query;
      }
    }

    return new Response(
      JSON.stringify({
        success: true,
        requestId: statusJson.id ?? requestId,
        externalId: statusJson.externalId ?? externalId,
        status: statusJson.status,
        amount: statusJson.amount,
        currency: statusJson.currency ?? 'UGX',
        payer: statusJson.payer,
        createdAt: statusJson.createdAt,
        updatedAt: statusJson.updatedAt,
      }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    );
  } catch (error) {
    console.error('Iotec status check error:', error);
    return new Response(
      JSON.stringify({ success: false, message: error instanceof Error ? error.message : 'Unknown error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    );
  }
});

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
