package io.indico.api;

import com.google.gson.Gson;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.indico.api.results.BatchIndicoResult;
import io.indico.api.results.IndicoResult;
import io.indico.api.utils.ImageUtils;
import io.indico.api.utils.IndicoException;

public class ApiClient {
    private final static String PUBLIC_BASE_URL = "https://apiv2.indico.io";

    private static HttpClient httpClient = HttpClients.createDefault();
    public String baseUrl;
    public String baseEndpoint;
    public String batchEndpoint;

    public ApiClient(String apiKey, String privateCloud) {
        this(PUBLIC_BASE_URL, apiKey, privateCloud);
    }

    IndicoResult call(Api api, BufferedImage data, String type, Map<String, Object> extraParams)
            throws UnsupportedOperationException, IOException, IndicoException {
        return call(api, ImageUtils.encodeImage(data, type), extraParams);
    }

    IndicoResult call(Api api, String data, Map<String, Object> extraParams)
            throws UnsupportedOperationException, IOException, IndicoException {

        Map<String, ?> apiResponse = baseCall(api, data, extraParams);
        return new IndicoResult(api, apiResponse);
    }

    BatchIndicoResult call(Api api, List<BufferedImage> data, String type, Map<String, Object> extraParams)
            throws UnsupportedOperationException, IOException, IndicoException {
        List<String> batchedData = new ArrayList<>(data.size());
        for (BufferedImage image : data)
            batchedData.add(ImageUtils.encodeImage(image, type));
        return call(api, batchedData, extraParams);
    }

    BatchIndicoResult call(Api api, List<String> data, Map<String, Object> extraParams)
            throws UnsupportedOperationException, IOException, IndicoException {

        Map<String, List<?>> apiResponse = baseCall(api, data, extraParams);
        return new BatchIndicoResult(api, apiResponse);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> baseCall(Api api, String data, Map<String, Object> extraParams)
            throws UnsupportedOperationException, IOException, IndicoException {
        HttpResponse response = httpClient.execute(getBasePost(api, data, extraParams, false));
        HttpEntity entity = response.getEntity();

        @SuppressWarnings("rawtypes")
        Map<String, ?> apiResponse = new HashMap();
        if (entity != null) {
            InputStream responseStream = entity.getContent();
            try {
                String responseString = IOUtils.toString(responseStream, "UTF-8");
                apiResponse = new Gson().fromJson(responseString, Map.class);
                if (apiResponse.containsKey("error")) {
                    throw new IllegalArgumentException((String) apiResponse.get("error"));
                }
            } finally {
                responseStream.close();
            }
        }
        return apiResponse;

    }

    @SuppressWarnings("unchecked")
    private Map<String, List<?>> baseCall(Api api, List<String> data, Map<String, Object> extraParams)
            throws IOException, IndicoException {
        HttpResponse response = httpClient.execute(getBasePost(api, data, extraParams, true));
        HttpEntity entity = response.getEntity();

        Map<String, List<?>> apiResponse = new HashMap<>();
        if (entity != null) {
            InputStream responseStream = entity.getContent();
            Reader reader = new InputStreamReader(responseStream, "UTF-8");
            try {
                apiResponse = new Gson().fromJson(reader, Map.class);
            } finally {
                responseStream.close();
            }
        }
        return apiResponse;
    }

    private HttpPost getBasePost(Api api, Object data, Map<String, Object> extraParams, boolean batch)
            throws UnsupportedEncodingException, IndicoException {
        String url = String.format(batch ? batchEndpoint : baseEndpoint, api) + addUrlParams(api, extraParams);

        HttpPost basePost = new HttpPost(url);

        Map<String, Object> rawParams = new HashMap<>();
        if (extraParams != null && !extraParams.isEmpty())
            rawParams.putAll(extraParams);
        rawParams.put("data", data);

        String entity = new Gson().toJson(rawParams);
        StringEntity params = new StringEntity(entity);
        params.setContentType("application/json");
        basePost.setEntity(params);

        basePost.addHeader("content-type", "application/json");
        basePost.addHeader("client-lib", "java");
        basePost.addHeader("client-lib", "1.4");

        return basePost;
    }

    private String addUrlParams(Api api, Map<String, Object> extraParams) throws IndicoException {
        StringBuilder builder = new StringBuilder();
        if (api == Api.MultiImage || api == Api.MultiText) {
            if (!extraParams.containsKey("apis"))
                throw new IndicoException("Apis argument for predictImage cannot be empty");
            Api[] apis = (Api[]) extraParams.get("apis");
            if (apis.length == 0)
                throw new IndicoException("Apis argument for predictImage cannot be empty");
            builder.append("&apis=");
            for (Api each : apis) {
                if (api.isImage() != each.isImage())
                    throw new IndicoException(api.name() + "is not an " + api.type + "Api");
                builder.append(each.getName()).append(",");
            }

            extraParams.remove("apis");
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    private ApiClient(String baseUrl, String apiKey, String privateCloud) {
        if (apiKey == null) {
            throw new IllegalArgumentException("API key cannot be null");
        }

        this.baseUrl = privateCloud == null ?
                baseUrl : "https://" + privateCloud + ".indico.domains";
        this.baseEndpoint = this.baseUrl + "/%1$2s?key=" + apiKey;
        this.batchEndpoint = this.baseUrl + "/%1$2s/batch" + "?key=" + apiKey;
    }
}
