package com.learningmachine.android.app.data;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.learningmachine.android.app.data.bitcoin.BitcoinManager;
import com.learningmachine.android.app.data.error.CertificateOwnershipException;
import com.learningmachine.android.app.data.model.LMDocument;
import com.learningmachine.android.app.data.model.Recipient;
import com.learningmachine.android.app.data.store.CertificateStore;
import com.learningmachine.android.app.data.webservice.CertificateService;
import com.learningmachine.android.app.data.webservice.response.AddCertificateResponse;
import com.learningmachine.android.app.util.FileUtils;

import java.io.IOException;

import okhttp3.ResponseBody;
import rx.Observable;

public class CertificateManager {

    private Context mContext;
    private CertificateStore mCertificateStore;
    private CertificateService mCertificateService;
    private BitcoinManager mBitcoinManager;

    public CertificateManager(Context context, CertificateStore certificateStore, CertificateService certificateService, BitcoinManager bitcoinManager) {
        mContext = context;
        mCertificateStore = certificateStore;
        mCertificateService = certificateService;
        mBitcoinManager = bitcoinManager;
    }

    /**
     * Currently returns a static filepath until saving of certificates is implemented
     *
     * @param uuid document.assertion.uid from the Certificate's json
     * @return filepath for the certificates json
     */
    public String getCertificateJsonFileUrl(String uuid) {
        return "file:///android_asset/sample-certificate.json";
    }

    public Observable<Void> addCertificate(String url) {
        return Observable.combineLatest(mCertificateService.getCertificate(url),
                mBitcoinManager.getBitcoinAddress(),
                AddCertificateHolder::new)
                .flatMap(holder -> handleCertificateResponse(holder.getResponseBody(), holder.getBitcoinAddress()));
    }

    /**
     * @param responseBody   Unparsed certificate response json
     * @param bitcoinAddress Wallet receive address
     * @return true if save was successful
     */
    private Observable<Void> handleCertificateResponse(ResponseBody responseBody, String bitcoinAddress) {
        try {
            Gson gson = new Gson();
            AddCertificateResponse addCertificateResponse = gson.fromJson(responseBody.string(),
                    AddCertificateResponse.class);
            LMDocument document = addCertificateResponse.getDocument();
            Recipient recipient = document.getRecipient();
            String recipientKey = recipient.getPublicKey();

            if (!bitcoinAddress.equals(recipientKey)) {
                return Observable.error(new CertificateOwnershipException());
            }

            mCertificateStore.saveAddCertificateResponse(addCertificateResponse);

            String uuid = document.getLMAssertion()
                    .getUuid();
            FileUtils.saveCertificate(mContext, responseBody, uuid);
            return null;
        } catch (JsonSyntaxException | IOException e) {
            return Observable.error(e);
        }
    }

    static class AddCertificateHolder {
        private final ResponseBody mResponseBody;
        private final String mBitcoinAddress;

        public AddCertificateHolder(ResponseBody responseBody, String bitcoinAddress) {
            mResponseBody = responseBody;
            mBitcoinAddress = bitcoinAddress;
        }

        public ResponseBody getResponseBody() {
            return mResponseBody;
        }

        public String getBitcoinAddress() {
            return mBitcoinAddress;
        }
    }
}