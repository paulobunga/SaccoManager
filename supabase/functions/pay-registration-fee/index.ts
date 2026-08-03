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
      userId,
      membershipNumber,
      amount,
      method = 'MOBILE_MONEY',
      phoneNumber,
      reference,
      notes,
    } = body;

    if (!userId || !membershipNumber || !amount) {
      return new Response(
        JSON.stringify({ success: false, message: 'userId, membershipNumber, and amount are required.' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';

    if (!supabaseUrl || !supabaseServiceRoleKey) {
      return new Response(
        JSON.stringify({ success: false, message: 'Supabase credentials are not configured.' }),
        { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const supabase = createClient<Database>(supabaseUrl, supabaseServiceRoleKey);

    // Upsert membership number on user record if provided
    if (membershipNumber) {
      const { error: updateError } = await supabase
        .from('users_registration')
        .update({ membershipNumber, updatedAt: new Date().toISOString() })
        .eq('id', userId);

      if (updateError) {
        return new Response(
          JSON.stringify({ success: false, message: updateError.message }),
          { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        );
      }
    }

    // Record registration fee payment
    const paymentRow = {
      member_id: userId,
      member_name: membershipNumber,
      cycle_month_index: new Date().getMonth() + 1,
      cycle_year: new Date().getFullYear(),
      amount_paid: amount,
      remaining_balance: 0,
      date_paid: new Date().toISOString(),
      status: 'PENDING',
      receipt_number: reference ?? `REG-${Date.now()}`,
      transaction_id: reference ?? `REG-${Date.now()}`,
      bank_name: method,
      notes: notes ?? 'Registration fee payment',
      _syncTimestamp: Date.now(),
      _actionType: 'REGISTRATION_FEE',
    };

    const { error: paymentError } = await supabase.from('savings_payments').insert(paymentRow);

    if (paymentError) {
      return new Response(
        JSON.stringify({ success: false, message: paymentError.message }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    return new Response(
      JSON.stringify({
        success: true,
        membershipNumber,
        payment: {
          receiptNumber: paymentRow.receipt_number,
          amount,
          status: 'PENDING',
        },
      }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    );
  } catch (error) {
    console.error('pay-registration-fee error:', error);
    return new Response(
      JSON.stringify({ success: false, message: error instanceof Error ? error.message : 'Unknown error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    );
  }
});
