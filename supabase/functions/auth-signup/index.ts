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
    const { email, password, name, phone, role = 'MEMBER' } = body;

    if (!email || !password || !name) {
      return new Response(
        JSON.stringify({ success: false, message: 'email, password, and name are required.' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
    const clerkSecretKey = Deno.env.get('CLERK_SECRET_KEY') ?? '';

    if (!supabaseUrl || !supabaseServiceRoleKey || !clerkSecretKey) {
      return new Response(
        JSON.stringify({ success: false, message: 'Server credentials are not configured.' }),
        { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const supabase = createClient<Database>(supabaseUrl, supabaseServiceRoleKey);

    // 1. Create user in Clerk
    const clerkResponse = await fetch('https://api.clerk.com/v1/sign_ups', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${clerkSecretKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email_address: [email],
        password,
      }),
    });

    const clerkData = await clerkResponse.json() as any;

    if (!clerkResponse.ok || clerkData.id == null) {
      return new Response(
        JSON.stringify({ success: false, message: clerkData.errors?.[0]?.long_message ?? 'Clerk sign-up failed.' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const clerkUserId = clerkData.id as string;

    // 2. Upsert local registration row
    const { error: userError } = await supabase.from('users_registration').upsert(
      {
        id: clerkUserId,
        email,
        phone: phone ?? null,
        name,
        role,
        status: 'PENDING',
        membership_number: null,
        clerk_user_id: clerkUserId,
        _syncTimestamp: Date.now(),
        _actionType: 'USER_REGISTER',
      },
      { onConflict: 'id' }
    );

    if (userError) {
      return new Response(
        JSON.stringify({ success: false, message: userError.message }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    return new Response(
      JSON.stringify({
        success: true,
        user: {
          id: clerkUserId,
          email,
          name,
          role,
          status: 'PENDING',
          membershipNumber: null,
        },
      }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    );
  } catch (error) {
    console.error('auth-signup error:', error);
    return new Response(
      JSON.stringify({ success: false, message: error instanceof Error ? error.message : 'Unknown error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    );
  }
});
