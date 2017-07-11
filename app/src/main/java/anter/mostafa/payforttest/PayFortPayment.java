package anter.mostafa.payforttest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.payfort.fort.android.sdk.base.FortSdk;
import com.payfort.fort.android.sdk.base.callbacks.FortCallBackManager;
import com.payfort.sdk.android.dependancies.base.FortInterfaces;
import com.payfort.sdk.android.dependancies.models.FortRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by mostafa_anter on 7/6/17.
 */

public class PayFortPayment {
    //Request key for response
    public static final int RESPONSE_GET_TOKEN = 111;
    public static final int RESPONSE_PURCHASE = 222;
    public static final int RESPONSE_PURCHASE_CANCEL = 333;
    public static final int RESPONSE_PURCHASE_SUCCESS = 444;
    public static final int RESPONSE_PURCHASE_FAILURE = 555;

    //WS params
    private final static String KEY_MERCHANT_IDENTIFIER = "merchant_identifier";
    private final static String KEY_SERVICE_COMMAND = "service_command";
    private final static String KEY_DEVICE_ID = "device_id";
    private final static String KEY_LANGUAGE = "language";
    private final static String KEY_ACCESS_CODE = "access_code";
    private final static String KEY_SIGNATURE = "signature";

    //Commands
    public final static String AUTHORIZATION = "AUTHORIZATION";
    public final static String PURCHASE = "PURCHASE";
    private final static String SDK_TOKEN = "SDK_TOKEN";

    //Test token url
    private final static String TEST_TOKEN_URL = "https://sbpaymentservices.payfort.com/FortAPI/paymentApi";
    //Live token url
    public final static String LIVE_TOKEN_URL = "https://paymentservices.payfort.com/FortAPI/paymentApi";
    //Make a change for live or test token url from WS_GET_TOKEN variable
    private final static String WS_GET_TOKEN = TEST_TOKEN_URL;

    //Statics
    private final static String MERCHANT_IDENTIFIER = "VZYfCwhg";
    private final static String ACCESS_CODE = "qeoqGtJrSmvtd8xZ3UBC";
    private final static String SHA_TYPE = "SHA-256";
    private final static String SHA_REQUEST_PHRASE = "TESTSHAIN";
    public final static String SHA_RESPONSE_PHRASE = "TESTSHAIN";
    public final static String CURRENCY_TYPE = "EGP";
    public final static String LANGUAGE_TYPE = "en";//Arabic - ar //English - en

    private final Gson gson;
    private Activity context;
    private IPaymentRequestCallBack iPaymentRequestCallBack;
    private FortCallBackManager fortCallback = null;
    private ProgressDialog progressDialog;
    private String sdkToken;
    private PayFortData payFortData;

    public PayFortPayment(Activity context, FortCallBackManager fortCallback, IPaymentRequestCallBack iPaymentRequestCallBack) {
        this.context = context;
        this.fortCallback = fortCallback;
        this.iPaymentRequestCallBack = iPaymentRequestCallBack;

        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage("Processing your payment\nPlease wait...");
        progressDialog.setCancelable(false);
        sdkToken = "";
        gson = new Gson();
    }

    public void requestForPayment(PayFortData payFortData) {
        this.payFortData = payFortData;
        new GetTokenFromServer().execute(WS_GET_TOKEN);
    }

    private void requestPurchase() {
        try {
            FortSdk.getInstance().registerCallback(context, getPurchaseFortRequest(), FortSdk.ENVIRONMENT.TEST, RESPONSE_PURCHASE,
                    fortCallback, new FortInterfaces.OnTnxProcessed() {
                        @Override
                        public void onCancel(Map<String, String> requestParamsMap, Map<String,
                                String> responseMap) {
                            JSONObject response = new JSONObject(responseMap);
                            PayFortData payFortData = gson.fromJson(response.toString(), PayFortData.class);
                            payFortData.paymentResponse = response.toString();
                            Log.e("Cancel Response", response.toString());
                            if (iPaymentRequestCallBack != null) {
                                iPaymentRequestCallBack.onPaymentRequestResponse(RESPONSE_PURCHASE_CANCEL, payFortData);
                            }
                        }

                        @Override
                        public void onSuccess(Map<String, String> requestParamsMap, Map<String,
                                String> fortResponseMap) {
                            JSONObject response = new JSONObject(fortResponseMap);
                            PayFortData payFortData = gson.fromJson(response.toString(), PayFortData.class);
                            payFortData.paymentResponse = response.toString();
                            Log.e("Success Response", response.toString());
                            if (iPaymentRequestCallBack != null) {
                                iPaymentRequestCallBack.onPaymentRequestResponse(RESPONSE_PURCHASE_SUCCESS, payFortData);
                            }
                        }

                        @Override
                        public void onFailure(Map<String, String> requestParamsMap, Map<String,
                                String> fortResponseMap) {
                            JSONObject response = new JSONObject(fortResponseMap);
                            PayFortData payFortData = gson.fromJson(response.toString(), PayFortData.class);
                            payFortData.paymentResponse = response.toString();
                            Log.e("Failure Response", response.toString());
                            if (iPaymentRequestCallBack != null) {
                                iPaymentRequestCallBack.onPaymentRequestResponse(RESPONSE_PURCHASE_FAILURE, payFortData);
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private FortRequest getPurchaseFortRequest() {
        FortRequest fortRequest = new FortRequest();
        if (payFortData != null) {
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put("amount", String.valueOf(payFortData.amount));
            parameters.put("command", payFortData.command);
            parameters.put("currency", payFortData.currency);
            parameters.put("customer_email", payFortData.customerEmail);
            parameters.put("language", payFortData.language);
            parameters.put("merchant_reference", payFortData.merchantReference);
            parameters.put("sdk_token", sdkToken);

            fortRequest.setRequestMap(parameters);
        }
        return fortRequest;
    }

    private class GetTokenFromServer extends AsyncTask<String, String, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... postParams) {
            String response = "";
            try {
                HttpURLConnection conn;
                URL url = new URL(postParams[0].replace(" ", "%20"));
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("content-type", "application/json");

                String str = getTokenParams();
                byte[] outputInBytes = str.getBytes("UTF-8");
                OutputStream os = conn.getOutputStream();
                os.write(outputInBytes);
                os.close();
                conn.connect();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = conn.getInputStream();
                    response = convertStreamToString(inputStream);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        }

        @Override
        protected void onPostExecute(String response) {
            super.onPostExecute(response);
            progressDialog.hide();
            Log.e("Response", response + "");
            try {
                PayFortData payFortData = gson.fromJson(response, PayFortData.class);
                if (!TextUtils.isEmpty(payFortData.sdkToken)) {
                    sdkToken = payFortData.sdkToken;
                    requestPurchase();
                } else {
                    payFortData.paymentResponse = response;
                    iPaymentRequestCallBack.onPaymentRequestResponse(RESPONSE_GET_TOKEN, payFortData);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String getTokenParams() {
        JSONObject jsonObject = new JSONObject();
        try {
            String device_id = FortSdk.getDeviceId(context);
            String concatenatedString = SHA_REQUEST_PHRASE +
                    KEY_ACCESS_CODE + "=" + ACCESS_CODE +
                    KEY_DEVICE_ID + "=" + device_id +
                    KEY_LANGUAGE + "=" + LANGUAGE_TYPE +
                    KEY_MERCHANT_IDENTIFIER + "=" + MERCHANT_IDENTIFIER +
                    KEY_SERVICE_COMMAND + "=" + SDK_TOKEN +
                    SHA_REQUEST_PHRASE;

            jsonObject.put(KEY_SERVICE_COMMAND, SDK_TOKEN);
            jsonObject.put(KEY_MERCHANT_IDENTIFIER, MERCHANT_IDENTIFIER);
            jsonObject.put(KEY_ACCESS_CODE, ACCESS_CODE);
            String signature = getSignatureSHA256(concatenatedString);
            jsonObject.put(KEY_SIGNATURE, signature);
            jsonObject.put(KEY_DEVICE_ID, device_id);
            jsonObject.put(KEY_LANGUAGE, LANGUAGE_TYPE);

            Log.e("concatenatedString", concatenatedString);
            Log.e("signature", signature);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.e("JsonString", String.valueOf(jsonObject));
        return String.valueOf(jsonObject);
    }

    private static String convertStreamToString(InputStream inputStream) {
        if (inputStream == null)
            return null;
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(inputStream), 1024);
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private static String getSignatureSHA256(String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = MessageDigest.getInstance(SHA_TYPE);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            return String.format("%0" + (messageDigest.length * 2) + 'x', new BigInteger(1, messageDigest));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }
}
