package com.example.sms_lemadio_sender;

/*public class ApiUrl {

    // public static final String API_URL = "http://102.16.254.214:8000/api/get_client_sales_info/";
    // public static final String SMS_USER_API_URL = "http://102.16.254.214:8000/";

    public static final String API_URL = "http://10.85.5.57:8000/api/get_client_sales_info/";
    public static final String SMS_USER_API_URL = "http://10.85.5.57:8000/";

}*/

public class ApiUrl {

    private static String ip = "";

    public static String getApiUrl() {
        return "http://" + ip + ":8000/api/get_client_sales_info/";
    }

    public static String getSmsUserApiUrl() {
        return "http://" + ip + ":8000/";
    }

    public static void setIp(String newIp) {
        ip = newIp;
    }
}