package store.xianglin;

import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用 HttpClient 模拟登录
 *
 * @author linxiang
 */
public class Simulation {
    private static final String SERVER_LOGIN_URL = "https://cas.server.com:8443/cas/login";
    private static final String CLIENT_HOST_URL = "https://client1.server.com:7443/";
    private static final CloseableHttpClient client;
    final static CookieStore cookieStore;

    static {
//        final SSLConnectionSocketFactory sslsf;
//        try {
//            sslsf = new SSLConnectionSocketFactory(SSLContext.getDefault(),
//                    NoopHostnameVerifier.INSTANCE);
//        } catch (NoSuchAlgorithmException e) {
//            throw new RuntimeException(e);
//        }
//
//        final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
//                .register("http", new PlainConnectionSocketFactory())
//                .register("https", sslsf)
//                .build();
//
//        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
//        cm.setMaxTotal(100);
//        client = HttpClients.custom()
//                .setSSLSocketFactory(sslsf)
//                .setConnectionManager(cm)
//                .build();

        //配置，发送https请求时，忽略ssl证书认证（否则会报错没有证书）
//        SSLContext sslContext;
//        try {
//            sslContext = SSLContexts.custom().loadTrustMaterial(null, (x509Certificates, s) -> true).build();
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        client = HttpClients.custom()
//                .setSslcontext(sslContext)
//                .build();

        cookieStore = new BasicCookieStore();
        final HttpHost httpHost = new HttpHost("127.0.0.1", 8866);
        final RequestConfig config = RequestConfig.custom()
                // 设置是否允许自动重定向
                .setRedirectsEnabled(false)
//                .setProxy(httpHost)
                .build();
        client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(config)
                // 允许 POST 请求自动重定向
//                .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
                // 忽略主机名验证
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();

//        client = HttpClients.createDefault();
    }


    /**
     * 模拟登录，返回包含 JSESSIONID 的 Cookie
     *
     * @param userName userName
     * @param password password
     * @return CookieValue
     * @throws IOException IOException
     */
    public String simulationLogin(String userName, String password) throws IOException {
        CloseableHttpResponse response;
        String url = SERVER_LOGIN_URL + "?service=" + CLIENT_HOST_URL;
        // 获取 CAS 登录页面
        HttpGet httpGet = new HttpGet(url);
        response = client.execute(httpGet);
        StatusLine statusLine = response.getStatusLine();
        int statusCode = statusLine.getStatusCode();
        if (HttpStatus.SC_OK != statusCode) {
            System.out.println("获取 CAS 登录页面返回错误！" + statusLine);
            return null;
        }
        String casLoginPageHtml = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        IOUtils.closeQuietly(response);
        // 使用 JSoup 解析表单
        Document document = Jsoup.parse(casLoginPageHtml);
        Element fm1Element = document.getElementById("fm1");
        Elements inputElements = fm1Element.getElementsByTag("input");
        // 提交参数
        List<NameValuePair> casLoginBody = new ArrayList<>(8);
        for (Element inputElement : inputElements) {
            String type = inputElement.attr("type");
            if ("submit".equals(type)) {
                continue;
            }
            String name = inputElement.attr("name");
            String value = inputElement.attr("value");
            if ("username".equals(name)) {
                value = userName;
            }
            if ("password".equals(name)) {
                value = password;
            }
            casLoginBody.add(new BasicNameValuePair(name, value));
        }
        // 提交登录表单
        HttpEntity loginEntity = new UrlEncodedFormEntity(casLoginBody, StandardCharsets.UTF_8);
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(loginEntity);
        response = client.execute(httpPost);
        statusLine = response.getStatusLine();
        if (HttpStatus.SC_MOVED_TEMPORARILY != statusLine.getStatusCode()) {
            System.out.println("登录 CAS 返回错误！" + statusLine);
            return null;
        }
        // 处理响应头
        Header cookie = response.getFirstHeader("Set-Cookie");
        System.out.println("cookie: " + cookie.getName() + " => " + cookie.getValue());
        Header location = response.getFirstHeader("Location");
        String locationUrl = location.getValue();
        System.out.println("locationUrl : " + locationUrl);
        IOUtils.closeQuietly(response);
        // 重定向到客户端
        httpGet = new HttpGet(locationUrl);
        response = client.execute(httpGet);
        statusLine = response.getStatusLine();
        if (HttpStatus.SC_MOVED_TEMPORARILY != statusLine.getStatusCode()) {
            System.out.println("登录客户端返回错误！" + statusLine);
            return null;
        }
        // 处理响应头，保存 Cookie
        cookie = response.getFirstHeader("Set-Cookie");
        System.out.println("cookie: " + cookie.getName() + " => " + cookie.getValue());
        location = response.getFirstHeader("Location");
        locationUrl = location.getValue();
        System.out.println("locationUrl : " + locationUrl);
        System.out.println("=======================");
        List<Cookie> cookieList = cookieStore.getCookies();
        for (Cookie cookie1 : cookieList) {
            System.out.println(cookie1.getName() + " => " + cookie1.getValue() + " => " + cookie1.getPath());
        }
        return cookie.getValue();
    }

    public void upload(String cookie, String filePath) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder().hostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", filePath.substring(filePath.lastIndexOf("/")),
                        RequestBody.create(MediaType.parse("application/octet-stream"),
                                new File(filePath)))
                .build();
        Request request = new Request.Builder()
                .url(CLIENT_HOST_URL + "upload")
                .method("POST", body)
                .addHeader("Cookie", cookie)
                .build();
        Response response = client.newCall(request).execute();
        ResponseBody responseBody = response.body();
        if (responseBody != null) {
            String string = responseBody.string();
            System.out.println(string);
        }
        IOUtils.closeQuietly(response);
        IOUtils.closeQuietly(responseBody);
    }
}
