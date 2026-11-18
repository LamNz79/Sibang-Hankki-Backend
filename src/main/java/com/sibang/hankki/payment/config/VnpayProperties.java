package com.sibang.hankki.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment.vnpay")
public class VnpayProperties {

    private boolean enabled;
    private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String tmnCode;
    private String hashSecret;
    private String returnUrl = "http://localhost:3000/reservations?payment=vnpay";
    private long depositAmount = 100_000L;
    private int expiryMinutes = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }
    public String getTmnCode() { return tmnCode; }
    public void setTmnCode(String tmnCode) { this.tmnCode = tmnCode; }
    public String getHashSecret() { return hashSecret; }
    public void setHashSecret(String hashSecret) { this.hashSecret = hashSecret; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public long getDepositAmount() { return depositAmount; }
    public void setDepositAmount(long depositAmount) { this.depositAmount = depositAmount; }
    public int getExpiryMinutes() { return expiryMinutes; }
    public void setExpiryMinutes(int expiryMinutes) { this.expiryMinutes = expiryMinutes; }

    public void validateForUse() {
        if (!enabled) {
            throw new IllegalStateException("VNPAY Sandbox is disabled");
        }
        if (tmnCode == null || tmnCode.isBlank() || hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalStateException("VNPAY Sandbox credentials are not configured");
        }
        if (depositAmount <= 0 || expiryMinutes <= 0) {
            throw new IllegalStateException("VNPAY payment amount and expiry must be positive");
        }
    }
}
