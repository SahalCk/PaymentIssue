package com.pigo.india.packages;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.JsonObject;
import com.pigo.india.modelsList.PackagesModel;
import com.pigo.india.packages.adapter.PaymentToastsModel;
import com.pigo.india.utills.AnalyticsTrackers;
import com.pigo.india.utills.Network.RestService;
import com.pigo.india.utills.SettingsMain;
import com.pigo.india.utills.UrlController;
import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;
import com.pigo.india.R;
import com.stripe.android.model.Token;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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
    public Button applyBtn;
    public ImageButton ValidateBtn;
    public EditText PromoCodeEt;
    public TextView PromoDescriptionTv,sTotalTv,PlanTv,discountTv,totalTv;


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
        applyBtn = findViewById(R.id.applyBtn);
        sTotalTv = findViewById(R.id.sTotalTv);
        ValidateBtn = findViewById(R.id.ValidateBtn);
        PromoCodeEt = findViewById(R.id.PromoCodeEt);
        PromoDescriptionTv = findViewById(R.id.PromoDescriptionTv);
        PlanTv = findViewById(R.id.PlanTv);
        discountTv = findViewById(R.id.discountTv);
        totalTv = findViewById(R.id.totalTv);

        if (isPromoCodeApplied){
            PromoDescriptionTv.setVisibility(View.VISIBLE);
            applyBtn.setVisibility(View.VISIBLE);
            applyBtn.setText("Applied");
            PromoCodeEt.setText(promoCode);
            PromoDescriptionTv.setText(description);
        }
        else {
            PromoDescriptionTv.setVisibility(View.GONE);
            applyBtn.setVisibility(View.GONE);
            applyBtn.setText("Apply");
        }


        if (!getIntent().getStringExtra("id").equals("")) {
            packageId = getIntent().getStringExtra("id");
            packageType = getIntent().getStringExtra("packageType");
            price = getIntent().getStringExtra("amount");
            packageName = getIntent().getStringExtra("packageName") ;

        }

        if (isPromoCodeApplied){
            priceWithDiscount();
        }
        else {
            priceWithoutDiscount();
        }


        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startPayment();
                Log.e(TAG, "onClick: lazy payment started");
            }
        });

        ValidateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String PromotionCode = PromoCodeEt.getText().toString().trim();
                if(TextUtils.isEmpty(PromotionCode)){
                    Toast.makeText(PayHereIntegration.this, "Please enter promo code...", Toast.LENGTH_SHORT).show();
                }
                else {
                    checkCodeAvailability(PromotionCode);
                }
            }
        });
        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isPromoCodeApplied=true;
                applyBtn.setText("Applied");
                priceWithDiscount();
            }
        });
    }
    private void priceWithDiscount(){
        discountTv.setText("₹"+promoPrice);
        sTotalTv.setText("₹"+price);
        PlanTv.setText(""+packageName);
        totalTv.setText("₹"+(Double.parseDouble(price)-(Double.parseDouble(promoPrice))));
    }

    private void priceWithoutDiscount() {
        discountTv.setText("₹00");
        sTotalTv.setText("₹"+price);
        PlanTv.setText(""+packageName);
        totalTv.setText("₹"+price);
    }

    public boolean isPromoCodeApplied = false;
    public String promoId,description,expiredate,minimumOrderPrice,promoCode,promoPrice;

    private void checkCodeAvailability(String PromotionCode){

        ProgressDialog ProgressDialog = new ProgressDialog(this);
        ProgressDialog.setTitle("Please Wait");
        ProgressDialog.setMessage("Checking Promo Code...");
        ProgressDialog.setCanceledOnTouchOutside(false);

        isPromoCodeApplied = false;
        applyBtn.setText("Apply");
        priceWithoutDiscount();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");
        ref.child("promotions").orderByChild("promoCode").equalTo(PromotionCode)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if(snapshot.exists()){
                            ProgressDialog.dismiss();
                            for (DataSnapshot ds: snapshot.getChildren()){
                                promoId=""+ds.child("id").getValue();
                                promoCode=""+ds.child("promoCode").getValue();
                                description=""+ds.child("description").getValue();
                                expiredate=""+ds.child("expireDate").getValue();
                                minimumOrderPrice=""+ds.child("minimumOrderPrice").getValue();
                                promoPrice=""+ds.child("promoPrice").getValue();

                                checkCodeExpireDate();

                            }
                        }
                        else {
                            ProgressDialog.dismiss();
                            Toast.makeText(PayHereIntegration.this, "Invalid promo coode", Toast.LENGTH_SHORT).show();
                            applyBtn.setVisibility(View.GONE);
                            PromoDescriptionTv.setVisibility(View.GONE);
                            PromoDescriptionTv.setText("");
                        }

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(PayHereIntegration.this, "Error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkCodeExpireDate() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH)+1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String todayDate = day +"/"+ month +"/"+ year;

        try {
            SimpleDateFormat sdformat = new SimpleDateFormat("dd/MM/yyyy");
            Date currentDate = sdformat.parse(todayDate);
            Date expireDate = sdformat.parse(expiredate);

            if(expireDate.compareTo(currentDate)>0){
                checkMinimumOrderPrice();
            }
            else if(expireDate.compareTo(currentDate)<0){
                Toast.makeText(this, "This promotion code is expired on "+expiredate, Toast.LENGTH_SHORT).show();
                applyBtn.setVisibility(View.GONE);
                PromoDescriptionTv.setVisibility(View.GONE);
                PromoDescriptionTv.setText("");
            }
            else if(expireDate.compareTo(currentDate)==0){
                checkMinimumOrderPrice();
            }
        }
        catch (Exception e){
            Toast.makeText(this, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
            applyBtn.setVisibility(View.GONE);
            PromoDescriptionTv.setVisibility(View.GONE);
            PromoDescriptionTv.setText("");
        }

    }

    private void checkMinimumOrderPrice() {
        if(Double.parseDouble(price)<Double.parseDouble(minimumOrderPrice)){
            Toast.makeText(this, "This code is valid for order with minimum amount: ?"+minimumOrderPrice, Toast.LENGTH_SHORT).show();
            applyBtn.setVisibility(View.GONE);
            PromoDescriptionTv.setVisibility(View.GONE);
            PromoDescriptionTv.setText("");
        }
        else {
            applyBtn.setVisibility(View.VISIBLE);
            PromoDescriptionTv.setVisibility(View.VISIBLE);
            PromoDescriptionTv.setText(description);
        }
    }


    public void startPayment() {
        Log.e(TAG, "startPayment: lazy start payment called");
        /*
          You need to pass current activity in order to let Razorpay create CheckoutActivity
         */
        final Activity activity = this;

        final Checkout co = new Checkout();



        try {
            JSONObject options = new JSONObject();
            options.put("name", "Pigo India");
            options.put("description", "An online platform to buy and sell pets");
            options.put("send_sms_hash",true);
            options.put("allow_rotation", true);
            //You can omit the image option to fetch the image from dashboard
            options.put("image", "https://cdn.razorpay.com/logos/GMBaBaARHVjVrt_medium.png");
            options.put("currency", "INR");
            options.put("amount",price);

            JSONObject preFill = new JSONObject();
            preFill.put("email", "pigoindia1@gmail.com");
            preFill.put("contact", "9544791688");

            options.put("prefill", preFill);

            co.open(activity, options);
            Log.e(TAG, "startPayment: lazy checkout called");
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



    private void adforest_Checkout() {

        Log.e(TAG, "onPaymentSuccess: lazy On payment adfrost checkout called");
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

    @Override
    public void onPaymentSuccess(String s) {
        try {
            Log.e(TAG, "onPaymentSuccess: lazy On payment success called");
            dialog.dismiss();
            adforest_Checkout();
        } catch (Exception e) {
            Log.e(TAG, "Exception in onPaymentSuccess", e);
        }
    }

    @Override
    public void onPaymentError(int i, String s) {
        try {
            Log.e(TAG, "onPaymentSuccess: lazy On payment failed called");
//            Toast.makeText(this, "Payment failed: " + code + " " + response, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Exception in onPaymentError", e);
        }
    }
}
