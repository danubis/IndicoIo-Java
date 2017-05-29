package io.indico.api;

import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.indico.api.results.BatchIndicoResult;
import io.indico.api.results.IndicoResult;
import io.indico.api.utils.IndicoException;


public class ApiClient {
    private final static String PUBLIC_BASE_URL = "https://apiv2.indico.io";
    private final static MediaType JSON = 
    		MediaType.parse("application/json; charset=utf-8");

    private static OkHttpClient httpClient = new OkHttpClient();
//    private static HttpClient httpClient = HttpClients.createDefault();
    public String baseUrl, apiKey, privateCloud;

    public ApiClient(String apiKey, String privateCloud) throws IndicoException {
        this(PUBLIC_BASE_URL, apiKey, privateCloud);
    }


    IndicoResult call(Api api, String data, Map<String, Object> extraParams)
        throws UnsupportedOperationException, IOException, IndicoException {

        Map<String, ?> apiResponse = baseCall(api, data, false, extraParams);
        return new IndicoResult(api, apiResponse);
    }

    
    @SuppressWarnings("unchecked")
    BatchIndicoResult call(Api api, Map<String, Object> data, Map<String, Object> extraParams)
        throws UnsupportedOperationException, IOException, IndicoException {
        Map<String, List<?>> apiResponse = (Map<String, List<?>>) baseCall(api, data, true, extraParams);
        return new BatchIndicoResult(api, apiResponse);
    }

    
    @SuppressWarnings("unchecked")
    BatchIndicoResult call(Api api, List<String> data, Map<String, Object> extraParams)
        throws UnsupportedOperationException, IOException, IndicoException {

        Map<String, List<?>> apiResponse = (Map<String, List<?>>) baseCall(api, data, true, extraParams);
        return new BatchIndicoResult(api, apiResponse);
    }

    
    Map<String, ?> baseCall(Api api, Object data, boolean batch, Map<String, Object> extraParams)
        throws UnsupportedOperationException, IOException, IndicoException {
        if (extraParams!= null && !extraParams.containsKey("version")) {
            extraParams.put("version", api.get("version") == null ? "1" : api.get("version"));
        }
        Response response = httpClient.newCall(getBasePost(api, data, extraParams, null, batch)).execute();

//        HttpResponse response = httpClient.execute(getBasePost(api, data, extraParams, null, batch));
        return handleResponse(response);
    }


    Map<String, ?> baseCall(Api api, Object data, boolean batch, String method, Map<String, Object> extraParams)
        throws UnsupportedOperationException, IOException, IndicoException {
        if (extraParams!= null && !extraParams.containsKey("version")) {
            extraParams.put("version", api.get("version") == null ? "1" : api.get("version"));
        }
        Response response = httpClient.newCall(getBasePost(api, data, extraParams, null, batch)).execute();

//        HttpResponse response = httpClient.execute(getBasePost(api, data, extraParams, method, batch));
        return handleResponse(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> handleResponse(Response response) throws IOException {
    	
//        HttpEntity entity = response.getEntity();
//        Header warning = response.getFirstHeader("X-Warning");

        ResponseBody entity = response.body();
        String warning = response.header("X-Warning");
        
        if (warning != null) {
            Logger.getLogger("indico").log(Level.WARNING, warning);
        }

        Map<String, ?> apiResponse = new HashMap<>();
        if (entity != null) {
            InputStream responseStream = entity.byteStream();
            Reader reader = new InputStreamReader(responseStream, "UTF-8");
            try {
                apiResponse = new Gson().fromJson(reader, Map.class);
            } finally {
                responseStream.close();
            }
        }
        return apiResponse;
    }

    private Request getBasePost(Api api, Object data, Map<String, Object> extraParams, String method, boolean batch)
        throws UnsupportedEncodingException, IndicoException {
    	
    	//URL construction stays the same
        String url = baseUrl
            + (api.type == ApiType.Multi ? "/apis" : "")
            + "/" + api.toString()
            + (batch ? "/batch" : "")
            + (method != null ? "/" + method : "")
            + addUrlParams(api, extraParams);
        
//        HttpPost basePost = new HttpPost(url);

        //body construction stays the same
        Map<String, Object> rawParams = new HashMap<>();
        if (api == Api.Persona) {
            rawParams.put("persona", true);
        }
        if (extraParams != null && !extraParams.isEmpty())
            rawParams.putAll(extraParams);
        if (data != null) {
            rawParams.put("data", data);
        }

        Object defaultParams = api.get("defaults");
        if (defaultParams != null) {
            rawParams.putAll((Map<String, Object>) defaultParams);
        }

        String entity = new Gson().toJson(rawParams);
//        StringEntity params = new StringEntity(entity, "utf-8");
//        params.setContentType("application/json");
//        basePost.setEntity(params);

//        basePost.addHeader("content-type", "application/json");
//        basePost.addHeader("client-lib", "java");
//        basePost.addHeader("client-lib", "3.2");
//        basePost.addHeader("Accept-Charset", "utf-8");
//        basePost.addHeader("X-ApiKey", apiKey);
        
        RequestBody body = RequestBody.create(JSON, entity);
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("content-type", "application/json")
            .addHeader("client-lib", "java")
            .addHeader("client-lib", "3.2")
            .addHeader("Accept-Charset", "utf-8")
            .addHeader("X-ApiKey", apiKey)
            .build();

        return request;
    }

    private String addUrlParams(Api api, Map<String, Object> extraParams) throws IndicoException {
        if (extraParams == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        if (api.type == ApiType.Multi) {
            if (!extraParams.containsKey("apis"))
                throw new IndicoException("Apis argument cannot be empty");
            Api[] apis = (Api[]) extraParams.get("apis");
            if (apis.length == 0)
                throw new IndicoException("Apis argument cannot be empty");
            builder.append("&apis=");
            for (Api each : apis) {
                if (api.get("type") != each.type)
                    throw new IndicoException(api.name() + "is not an " + api.type + "Api");
                builder.append(each.toString()).append(",");
            }

            extraParams.remove("apis");
            builder.deleteCharAt(builder.length() - 1);
        }

        if (extraParams.containsKey("version")) {
            String version = extraParams.get("version").toString();
            if (api == Api.Keywords && extraParams.get("language") != null && extraParams.get("language") != "english") {
                version = "1";
            }
            extraParams.remove("version");
            builder.append("&version=").append(version);
        }

        String result = builder.toString();

        return result.isEmpty() ? "" : "?" + result;
    }

    private ApiClient(String baseUrl, String apiKey, String privateCloud) throws IndicoException {
        if (apiKey == null) {
            throw new IndicoException("API key cannot be null");
        }

        this.baseUrl = privateCloud == null ?
            baseUrl : "https://" + privateCloud + ".indico.domains";
        this.apiKey = apiKey;
        this.privateCloud = privateCloud;
    }
}
