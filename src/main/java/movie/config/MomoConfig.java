package movie.config;

import org.springframework.context.annotation.Configuration;
import javax.servlet.http.HttpServletRequest;

@Configuration // Mark as a Spring configuration class
public class MomoConfig {
    public static final String momo_PartnerCode = "MOMO"; // Replace with actual Partner Code
    public static final String momo_AccessKey = "F8BBA842ECF85"; // Replace with actual Access Key
    public static final String momo_SecretKey = "K951B6PE1waDMi640xX08PD3vg6EkVlz"; // Replace with actual Secret Key
    public static final String momo_Url = "https://test-payment.momo.vn/v2/gateway/api/create";
    public static final String momo_RequestType = "captureWallet";

    // Method to create return URL dynamically based on context path
    public static String getReturnUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        return scheme + "://" + serverName + (serverPort == 80 || serverPort == 443 ? "" : ":" + serverPort)
                + contextPath + "/booking/momo-payment-return";
    }

    // Method to create notify URL dynamically based on context path
    public static String getNotifyUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        return scheme + "://" + serverName + (serverPort == 80 || serverPort == 443 ? "" : ":" + serverPort)
                + contextPath + "/booking/momo-notify";
    }
}