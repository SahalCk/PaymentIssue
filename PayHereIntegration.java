package com.ABCD.india.packages;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.ABCD.india.modelsList.PackagesModel;
import com.ABCD.india.packages.adapter.PaymentToastsModel;
import com.ABCD.india.utills.AnalyticsTrackers;
import com.ABCD.india.utills.Network.RestService;
import com.ABCD.india.utills.SettingsMain;
import com.ABCD.india.utills.UrlController;
import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;
import com.ABCD.india.R;
import com.stripe.android.model.Token;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import co.paystack.android.model.Charge;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PayHereIntegration extends Activity implements PaymentResultListener {
    private static final String TAG = PayHereIntegration.class.getSimpleName();


    String price,packageType,packageName;
    SettingsMain settingsMain;
    private String packageId = "";
    RestService restService;
    ProgressDialog dialog;
    Charge charge;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pay_here_integration);

        /*
         To ensure faster loading of the Checkout form,
          call this method as early as possible in your checkout flow.
         */
        Checkout.preload(getApplicationContext());

        // Payment button created by you in XML layout
        Button button = (Button) findViewById(R.id.btn_pay);

        if (!getIntent().getStringExtra("id").equals("")) {
            packageId = getIntent().getStringExtra("id");
            packageType = getIntent().getStringExtra("packageType");
            price = getIntent().getStringExtra("amount");
            packageName = getIntent().getStringExtra("packageName") ;

        }


        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPayment();
            }
        });

        TextView privacyPolicy = (TextView) findViewById(R.id.txt_privacy_policy);

        privacyPolicy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent httpIntent = new Intent(Intent.ACTION_VIEW);
                httpIntent.setData(Uri.parse("https://razorpay.com/sample-application/"));
                startActivity(httpIntent);
            }
        });
    }

    public void performCharge(Charge charge) {
        int a = (int) Math.round(Double.parseDouble(price));
        charge.setAmount(a * 100);
        finish();
        return;
    }

    public void startPayment() {
        /*
          You need to pass current activity in order to let Razorpay create CheckoutActivity
         */
        final Activity activity = this;

        final Checkout co = new Checkout();

        performCharge(charge);


        try {
            JSONObject options = new JSONObject();
            options.put("name", "ABCD");
            options.put("description", "description");
            options.put("send_sms_hash",true);
            options.put("allow_rotation", true);
            //You can omit the image option to fetch the image from dashboard
            options.put("image", "https://cdn.razorpay.com/logos/GMBaBaARHVjVrt_medium.png");
            options.put("currency", "INR");
            options.put("amount", price);

            JSONObject preFill = new JSONObject();
            preFill.put("email", "pigoindia1@gmail.com");
            preFill.put("contact", "9544791688");

            options.put("prefill", preFill);

            co.open(activity, options);
        } catch (Exception e) {
            Toast.makeText(activity, "Error in payment: " + e.getMessage(), Toast.LENGTH_SHORT)
                    .show();
            e.printStackTrace();
        }
    }

    /**
     * The name of the function has to be
     * onPaymentSuccess
     * Wrap your code in try catch, as shown, to ensure that this method runs correctly
     */


    @SuppressWarnings("unused")
    @Override
    public void onPaymentSuccess(String razorpayPaymentID) {
        try {
            dialog.dismiss();
            adforest_Checkout();
        } catch (Exception e) {
            Log.e(TAG, "Exception in onPaymentSuccess", e);
        }
    }

    /**
     * The name of the function has to be
     * onPaymentError
     * Wrap your code in try catch, as shown, to ensure that this method runs correctly
     */
    @SuppressWarnings("unused")
    @Override
    public void onPaymentError(int code, String response) {
        try {
            Toast.makeText(this, "Payment failed: " + code + " " + response, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Exception in onPaymentError", e);
        }
    }


    private void adforest_Checkout() {

        if (SettingsMain.isConnectingToInternet(PayHereIntegration.this)) {

            settingsMain.showDilog(PayHereIntegration.this);
            JsonObject params = new JsonObject();
            params.addProperty("package_id", packageId);
            params.addProperty("payment_from", packageType);
            Log.d("info Send Checkout", params.toString());

            Call<ResponseBody> myCall = restService.postCheckout(params, UrlController.AddHeaders(this));
            myCall.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> responseObj) {
                    try {
                        if (responseObj.isSuccessful()) {
                            Log.d("info Checkout Resp", "" + responseObj.toString());

                            JSONObject response = new JSONObject(responseObj.body().string());
                            Log.d("info Checkout object", "" + response.toString());
                            if (response.getBoolean("success")) {
                                settingsMain.setPaymentCompletedMessage(response.get("message").toString());
                                adforest_getDataForThankYou();
                            } else{
                                dialog.dismiss();
                                Toast.makeText(PayHereIntegration.this, response.get("message").toString(), Toast.LENGTH_SHORT).show();
                                finish();
                            }


                        }else{
                            dialog.dismiss();
                            Toast.makeText(PayHereIntegration.this, PaymentToastsModel.payment_failed + PaymentToastsModel.something_wrong, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } catch (JSONException e) {
                        SettingsMain.hideDilog();
                        e.printStackTrace();
                    } catch (IOException e) {
                        SettingsMain.hideDilog();
                        e.printStackTrace();
                    }
                }
               @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    dialog.dismiss();
                    if (t instanceof TimeoutException) {
                        Toast.makeText(getApplicationContext(), settingsMain.getAlertDialogMessage("internetMessage"), Toast.LENGTH_SHORT).show();
                        settingsMain.hideDilog();
                    }
                    if (t instanceof SocketTimeoutException || t instanceof NullPointerException) {

                        Toast.makeText(getApplicationContext(), settingsMain.getAlertDialogMessage("internetMessage"), Toast.LENGTH_SHORT).show();
                        settingsMain.hideDilog();
                    }
                    if (t instanceof NullPointerException || t instanceof UnknownError || t instanceof NumberFormatException) {
                        Log.d("info Checkout ", "NullPointert Exception" + t.getLocalizedMessage());
                        settingsMain.hideDilog();
                    } else {
                        SettingsMain.hideDilog();
                        Log.d("info Checkout err", String.valueOf(t));
                        Log.d("info Checkout err", String.valueOf(t.getMessage() + t.getCause() + t.fillInStackTrace()));
                    }
                }
            });
        } else {
            SettingsMain.hideDilog();
            Toast.makeText(PayHereIntegration.this, settingsMain.getAlertDialogTitle("error"), Toast.LENGTH_SHORT).show();
        }
    }


    public void adforest_getDataForThankYou() {
        if (SettingsMain.isConnectingToInternet(PayHereIntegration.this)) {
            Call<ResponseBody> myCall = restService.getPaymentCompleteData(UrlController.AddHeaders(PayHereIntegration.this));
            myCall.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> responseObj) {
                    try {
                        if (responseObj.isSuccessful()) {
                            Log.d("info ThankYou Details", "" + responseObj.toString());

                            JSONObject response = new JSONObject(responseObj.body().string());
                            if (response.getBoolean("success")) {
                                JSONObject responseData = response.getJSONObject("data");

                                Log.d("info ThankYou object", "" + response.getJSONObject("data"));

                                Intent intent = new Intent(PayHereIntegration.this, Thankyou.class);
                                intent.putExtra("data", responseData.getString("data"));
                                intent.putExtra("order_thankyou_title", responseData.getString("order_thankyou_title"));
                                intent.putExtra("order_thankyou_btn", responseData.getString("order_thankyou_btn"));
                                startActivity(intent);
                                SettingsMain.hideDilog();
                                PayHereIntegration.this.finish();
                            } else {
                               dialog.dismiss();
                                SettingsMain.hideDilog();
                                Toast.makeText(PayHereIntegration.this, response.get("message").toString(), Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        SettingsMain.hideDilog();
                        finish();
                    } catch (IOException e) {
                        e.printStackTrace();
                        SettingsMain.hideDilog();
                        finish();
                    }
               }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                   dialog.dismiss();
                    SettingsMain.hideDilog();
                    Log.d("info ThankYou error", String.valueOf(t));
                    Log.d("info ThankYou error", String.valueOf(t.getMessage() + t.getCause() + t.fillInStackTrace()));
                    finish();
                }
            });
        } else {
            SettingsMain.hideDilog();
            Toast.makeText(PayHereIntegration.this, "Internet error", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    }
