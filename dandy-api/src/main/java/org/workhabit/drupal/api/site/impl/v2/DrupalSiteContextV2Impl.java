package org.workhabit.drupal.api.site.impl.v2;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.json.JSONException;
import org.json.JSONObject;
import org.workhabit.drupal.api.entity.*;
import org.workhabit.drupal.api.json.BooleanAdapter;
import org.workhabit.drupal.api.json.DrupalJsonObjectSerializer;
import org.workhabit.drupal.api.json.DrupalJsonObjectSerializerFactory;
import org.workhabit.drupal.api.json.UnixTimeDateAdapter;
import org.workhabit.drupal.api.site.DrupalSiteContext;
import org.workhabit.drupal.api.site.exceptions.DrupalFetchException;
import org.workhabit.drupal.api.site.exceptions.DrupalLoginException;
import org.workhabit.drupal.api.site.exceptions.DrupalLogoutException;
import org.workhabit.drupal.api.site.exceptions.DrupalSaveException;
import org.workhabit.drupal.api.site.impl.DrupalSiteContextInstanceState;
import org.workhabit.drupal.api.site.support.GenericCookie;
import org.workhabit.drupal.api.site.support.HttpUrlConnectionFactory;
import org.workhabit.drupal.api.site.support.HttpUrlConnectionFactoryImpl;
import org.workhabit.drupal.http.DrupalServicesRequestManager;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Copyright 2009 - WorkHabit, Inc. - acs
 * Date: Sep 24, 2010, 12:21:03 PM
 */
public class DrupalSiteContextV2Impl implements DrupalSiteContext {

    private static final String JSON_SERVICE_PATH = "/services/json";
    private static final String SERVICE_NAME_COMMENT_LOAD = "comment.load";
    private static final String SERVICE_NAME_COMMENT_SAVE = "comment.save";
    private static final String SERVICE_NAME_USER_LOGIN = "user.login";
    private static final String SERVICE_NAME_VIEWS_GET = "views.get";
    private static final String SERVICE_NAME_TAXONOMY_DICTIONARY = "taxonomy.dictionary";
    private static final String SERVICE_NAME_FILE_SAVE = "file.save";
    private static final String SERVICE_NAME_FILE_GETDIRECTORYPATH = "file.getDirectoryPath";
    private static final String SERVICE_NAME_COMMENT_LOADNODECOMMENTS = "comment.loadNodeComments";
    private static final String SERVICE_NAME_NODE_SAVE = "node.save";
    private static final String SERVICE_NAME_CREATE_NEW_USER = "user.save";
    private static final String SERVICE_NAME_USER_GET = "user.get";
    private static final String SERVICE_NAME_NODE_GET = "node.get";

    private DrupalServicesRequestManager drupalServicesRequestManager;

    private String session;

    private DrupalUser user;
    private final String drupalSiteUrl;
    private final String servicePath;
    private ArrayList<GenericCookie> cookies;
    private HttpUrlConnectionFactory urlConnectionFactory;

    /**
     * Constructor takes an authentication token to use for the lifecycle of requests for this instance
     *
     * @param drupalSiteUrl site url to connect to
     */
    public DrupalSiteContextV2Impl(String drupalSiteUrl) {
        urlConnectionFactory = new HttpUrlConnectionFactoryImpl();
        this.drupalSiteUrl = drupalSiteUrl;
        this.servicePath = new StringBuilder().append(drupalSiteUrl).append(JSON_SERVICE_PATH).toString();
    }

    public DrupalSiteContextInstanceState getInstanceState() {
        DrupalSiteContextInstanceStateImpl state = new DrupalSiteContextInstanceStateImpl();
        state.setCookies(cookies);
        state.setUser(user);
        return state;
    }

    public void initializeSavedState(DrupalSiteContextInstanceState state) {
        urlConnectionFactory = new HttpUrlConnectionFactoryImpl();
        this.cookies = state.getCookies();
        this.user = state.getUser();
        drupalServicesRequestManager.initializeSavedState(state);
    }


    public void setUrlConnectionFactory(HttpUrlConnectionFactory urlConnectionFactory) {
        this.urlConnectionFactory = urlConnectionFactory;
    }

    public DrupalUser getCurrentUser() {
        return user;
    }

    /**
     * call system.connect on the current instance.  This is required for key authentication to work properly.
     */
    public void connect() throws DrupalFetchException {
        try {
            String result = drupalServicesRequestManager.post(servicePath, "system.connect", null, true);
            JSONObject object = new JSONObject(result);
            if ("true".equals(object.getString("#error"))) {
                throw new DrupalFetchException(object);
            }
            if (object.has("#data")) {
                try {
                    JSONObject data = object.getJSONObject("#data");
                    if (data != null) {
                        String sessid = data.getString("sessid");
                        if (sessid != null) {
                            setSession(sessid);
                        }

                    }
                } catch (Exception e) {
                    // no sessid returned
                }
            }
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    /**
     * Logs out the current user.  The user's session id is cleared, and Drupal is notified of the logout event.
     *
     * @throws DrupalLogoutException if there is a problem logging out the current user.  This can happen,
     *                               for example, if the user is already logged out.
     */
    public void logout() throws DrupalLogoutException {
        try {
            connect();
            String result = drupalServicesRequestManager.postSigned(servicePath, "user.logout", null, true);
            JSONObject object = new JSONObject(result);
            if ("true".equals(object.getString("#error"))) {
                throw new DrupalFetchException(object);
            }
            setSession(null);
            cookies = new ArrayList<GenericCookie>();
            user = null;
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalLogoutException(e);
        } catch (IOException e) {
            throw new DrupalLogoutException(e);
        } catch (JSONException e) {
            throw new DrupalLogoutException(e);
        } catch (DrupalFetchException e) {
            throw new DrupalLogoutException(e);
        } catch (InvalidKeyException e) {
            throw new DrupalLogoutException(e);
        }

    }

    /**
     * @param viewName the name of the view to return.  This is an override for {@link #getNodeView(String, String)}
     *                 for views that don't take any arguments.
     * @return list of drupal nodes corresponding to the result of the view.
     * @throws DrupalFetchException
     */
    public List<DrupalNode> getNodeView(String viewName) throws DrupalFetchException {
        return getNodeView(viewName, null);
    }

    /**
     * Helper function to check for error conditions in the returned JSON object.
     *
     * @param objectResult the JSONObject to check for errors.  The structure of this object is generally:
     *                     <p/>
     *                     <pre>
     *                                                                                                                                                                                                                                                                                                                                                                                             {
     *                                                                                                                                                                                                                                                                                                                                                                                               '#error': boolean
     *                                                                                                                                                                                                                                                                                                                                                                                               '#data': 'json string containing the result or error string if #error is true.'
     *                                                                                                                                                                                                                                                                                                                                                                                             }
     *                                                                                                                                                                                                                                                                                                                                                                                             </pre>
     * @throws JSONException        if there's an error deserializing the response.
     * @throws DrupalFetchException if an error occurred. The message of the exception contains the error.
     *                              See {@link org.workhabit.drupal.api.site.exceptions.DrupalFetchException#getMessage()}
     */
    protected void assertNoErrors(JSONObject objectResult) throws JSONException, DrupalFetchException {
        if (objectResult.has("#error") && objectResult.getBoolean("#error")) {
            throw new DrupalFetchException(objectResult);
        }
        // there's another case where #error is false, but #data contains an #error and message
        if (objectResult.has("#data")) {
            Object data = objectResult.get("#data");
            if (data instanceof JSONObject) {
                JSONObject dataObject = (JSONObject) data;
                if (dataObject.has("#error") && dataObject.getBoolean("#error")) {
                    if (dataObject.has("#message")) {
                        String error = dataObject.getString("#message");
                        throw new DrupalFetchException(error);
                    }
                }
            }
        }
    }

    /**
     * Fetches a Drupal Node by Node ID.
     *
     * @param nid the ID of the drupal node to fetch
     * @return Drupal Node if there's a match. Null otherwise.
     * @throws DrupalFetchException if there's an error fetching the node from Drupal, or if there's a
     *                              serialization problem.
     */
    public DrupalNode getNode(int nid) throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("nid", nid);
        data.put("sessid", session);
        try {
            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_NODE_GET, data, true);
            JSONObject object = new JSONObject(result);
            assertNoErrors(object);
            DrupalJsonObjectSerializer<DrupalNode> serializer = DrupalJsonObjectSerializerFactory.getInstance(DrupalNode.class);
            return serializer.unserialize(result);
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    /**
     * Fetches an individual comment from Drupal based on CID
     *
     * @param cid the id of the comment to fetch
     * @return a DrupalComment object representing the comment data, null otherwise.
     * @throws DrupalFetchException if there's a problem fetching the comment.
     */
    public DrupalComment getComment(int cid) throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("cid", cid);
        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }

        try {
            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_COMMENT_LOAD, data, true);
            DrupalJsonObjectSerializer<DrupalComment> serializer = DrupalJsonObjectSerializerFactory.getInstance(DrupalComment.class);
            return serializer.unserialize(result);
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    public int saveComment(final DrupalComment comment) throws DrupalFetchException {
        connect();
        GsonBuilder builder = new GsonBuilder();
        ExclusionStrategy strategy = new ExclusionStrategy() {
            public boolean shouldSkipField(FieldAttributes f) {
                if ("cid".equals(f.getName())) {
                    if (comment.getCid() == 0) {
                        return true;
                    }
                }
                if ("uid".equals(f.getName())) {
                    if (comment.getUid() == 0) {
                        return true;
                    }
                }
                return false;
            }

            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        };
        builder.setExclusionStrategies(strategy);
        Gson gson = builder.create();
        String jsonComment = gson.toJson(comment);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("comment", unicodeEscape(jsonComment));
        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }

        try {
            String response = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_COMMENT_SAVE, data, false);
            JSONObject jsonObject = new JSONObject(response);
            assertNoErrors(jsonObject);
            if (jsonObject.has("#data") && !jsonObject.isNull("#data")) {
                try {
                    return jsonObject.getInt("#data");
                } catch (JSONException e) {
                    // probably wasn't an int.
                    return jsonObject.getBoolean("#data") ? 1 : 0;
                }
            }
            return 0;
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalSaveException(e);
        } catch (IOException e) {
            throw new DrupalSaveException(e);
        } catch (InvalidKeyException e) {
            throw new DrupalSaveException(e);
        } catch (JSONException e) {
            throw new DrupalSaveException(e);
        }

    }

    public DrupalUser login(String username, String password) throws DrupalLoginException, DrupalFetchException {
        connect();
        if (session != null && user != null) {
            return user;
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("username", username);
        data.put("password", password);

        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }

        try {
            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_USER_LOGIN, data, true);
            return processLoginResult(result);
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    public List<DrupalTaxonomyTerm> getTermView(String viewName) throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("view_name", viewName);
        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }

        try {
            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_VIEWS_GET, data, true);
            return processGetTermViewResult(result);
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    public List<DrupalTaxonomyTerm> getCategoryList() throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }

        data.put("vid", 1);
        try {
            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_TAXONOMY_DICTIONARY, data, true);
            return processGetTermViewResult(result);
        } catch (JSONException e) {
            throw new DrupalFetchException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalFetchException(e);
        } catch (IOException e) {
            throw new DrupalFetchException(e);
        } catch (InvalidKeyException e) {
            throw new DrupalFetchException(e);
        }
    }

    public int registerNewUser(String username, String password, String email) throws DrupalSaveException {
        try {
            connect();
            Map<String, Object> data = new HashMap<String, Object>();
            final DrupalUser user = new DrupalUser();
            user.setName(username);
            user.setPassword(password);
            user.setMail(email);

            GsonBuilder builder = new GsonBuilder();
            ExclusionStrategy strategy = new ExclusionStrategy() {
                public boolean shouldSkipField(FieldAttributes f) {
                    if ("uid".equals(f.getName())) {
                        if (user.getUid() == 0) {
                            return true;
                        }
                    }
                    return false;
                }

                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            };
            builder.setExclusionStrategies(strategy);
            Gson gson = builder.create();
            String jsonUser = gson.toJson(user);

            data.put("account", unicodeEscape(jsonUser));
            data.put("sessid", session);

            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_CREATE_NEW_USER, data, false);
            JSONObject object = new JSONObject(result);
            assertNoErrors(object);
            return object.getInt("#data");
        } catch (JSONException e) {
            throw new DrupalSaveException(e);
        } catch (DrupalFetchException e) {
            throw new DrupalSaveException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalSaveException(e);
        } catch (InvalidKeyException e) {
            throw new DrupalSaveException(e);
        } catch (IOException e) {
            throw new DrupalSaveException(e);
        }
    }

    public String getFileDirectoryPath() throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("sessid", session);
        try {
            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_FILE_GETDIRECTORYPATH, data, true);
            JSONObject objectResult = new JSONObject(result);
            assertNoErrors(objectResult);
            return objectResult.getString("#data");
        } catch (JSONException e) {
            throw new DrupalFetchException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalFetchException(e);
        } catch (InvalidKeyException e) {
            throw new DrupalFetchException(e);
        } catch (IOException e) {
            throw new DrupalFetchException(e);
        }
    }

    public List<DrupalComment> getComments(int nid, int start, int count) throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("nid", nid);
        // TODO: Parameterize these
        data.put("count", count);
        data.put("start", start);

        data.put("sessid", session);
        try {
            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_COMMENT_LOADNODECOMMENTS, data, true);
            JSONObject objectResult = new JSONObject(result);
            assertNoErrors(objectResult);
            DrupalJsonObjectSerializer<DrupalComment> serializer = DrupalJsonObjectSerializerFactory.getInstance(DrupalComment.class);
            return serializer.unserializeList(result);
        } catch (IOException e) {
            throw new DrupalFetchException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalFetchException(e);
        } catch (InvalidKeyException e) {
            throw new DrupalFetchException(e);
        } catch (JSONException e) {
            throw new DrupalFetchException(e);
        }
    }

    public List<DrupalComment> getComments(int nid) throws DrupalFetchException {
        return getComments(nid, 0, 0);
    }

    public InputStream getFileStream(String filepath) throws IOException {
        return drupalServicesRequestManager.getStream(drupalSiteUrl + "/" + filepath);
    }

    public List<DrupalNode> getNodeView(String viewName, String viewArguments) throws DrupalFetchException {
        return getNodeView(viewName, viewArguments, -1, -1);
    }

    public List<DrupalNode> getNodeView(String viewName, String viewArguments, int offset, int limit) throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("view_name", viewName);
        if (viewArguments != null) {
            data.put("args", viewArguments);
        }
        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }
        if (offset > 0 && limit > 0) {
            data.put("limit", limit);
            data.put("offset", offset);
        }
        try {
            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_VIEWS_GET, data, true);
            assertNoErrors(new JSONObject(result));
            DrupalJsonObjectSerializer<DrupalNode> serializer = DrupalJsonObjectSerializerFactory.getInstance(DrupalNode.class);
            return serializer.unserializeList(result);
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    private List<DrupalTaxonomyTerm> processGetTermViewResult(String result) throws DrupalFetchException, JSONException {
        DrupalJsonObjectSerializer<DrupalTaxonomyTerm> serializer = DrupalJsonObjectSerializerFactory.getInstance(DrupalTaxonomyTerm.class);
        return serializer.unserializeList(result);
    }

    private DrupalUser processLoginResult(String result) throws JSONException, DrupalFetchException, DrupalLoginException {
        JSONObject objectResult = new JSONObject(result);
        assertNoErrors(objectResult);

        JSONObject dataObject = objectResult.getJSONObject("#data");
        setSession(dataObject.getString("sessid"));
        DrupalJsonObjectSerializer<DrupalUser> serializer = DrupalJsonObjectSerializerFactory.getInstance(DrupalUser.class);
        user = serializer.unserialize(dataObject.getJSONObject("user").toString());

        cookies = drupalServicesRequestManager.getCookies();
        return user;
    }

    public int saveNode(final DrupalNode node) throws DrupalSaveException {
        try {
            connect();
            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter(Date.class, new UnixTimeDateAdapter());
            builder.registerTypeAdapter(boolean.class, new BooleanAdapter());
            ExclusionStrategy strategy = new ExclusionStrategy() {
                public boolean shouldSkipField(FieldAttributes f) {
                    if ("nid".equals(f.getName())) {
                        if (node.getNid() == 0) {
                            return true;
                        }
                    }
                    if ("fields".equals(f.getName())) {
                        return true;
                    }
                    return false;
                }

                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            };
            builder.setExclusionStrategies(strategy);
            builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
            Gson gson = builder.create();
            Map<String, Object> data = new HashMap<String, Object>();
            JsonObject jsonNode = (JsonObject) gson.toJsonTree(node);
            Map<String, DrupalField> fields = node.getFields();
            Type type = new TypeToken<Map<String, String>>() {
                }.getType();
            if (fields != null) {
                for (Map.Entry<String, DrupalField> entry : fields.entrySet()) {
                    String name = entry.getKey();
                    JsonObject fieldObject = new JsonObject();
                    ArrayList<HashMap<String, String>> values = entry.getValue().getValues();
                    for (int i = 0; i < values.size(); i++) {
                        HashMap<String, String> map = values.get(i);
                        JsonObject valueObject = new JsonObject();
                        for (Map.Entry<String, String> valueEntry : map.entrySet()) {
                            if (valueEntry.getValue().startsWith("{")) {
                                Map<String, String> element = gson.fromJson(valueEntry.getValue(), type);
                                valueObject.add(valueEntry.getKey(), gson.toJsonTree(element));
                            } else {
                                valueObject.addProperty(valueEntry.getKey(), valueEntry.getValue());
                            }
                        }
                        fieldObject.add(String.valueOf(i), valueObject);
                    }
                    jsonNode.add(name, fieldObject);
                }
            }
            data.put("node", unicodeEscape(jsonNode.toString()));
            data.put("sessid", session);

            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_NODE_SAVE, data, false);
            JSONObject objectResult = new JSONObject(result);
            assertNoErrors(objectResult);
            return objectResult.getInt("#data");
        } catch (IOException e) {
            throw new DrupalSaveException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalSaveException(e);
        } catch (InvalidKeyException e) {
            throw new DrupalSaveException(e);
        } catch (DrupalFetchException e) {
            throw new DrupalSaveException(e);
        } catch (JSONException e) {
            throw new DrupalSaveException(e);
        }
    }

    private static final char[] hexChar = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };


    private String unicodeEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >> 7) > 0) {
                sb.append("\\u");
                sb.append(hexChar[(c >> 12) & 0xF]); // append the hex character for the left-most 4-bits
                sb.append(hexChar[(c >> 8) & 0xF]);  // hex for the second group of 4-bits from the left
                sb.append(hexChar[(c >> 4) & 0xF]);  // hex for the third group
                sb.append(hexChar[c & 0xF]);         // hex for the last group, e.g., the right most 4-bits
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public DrupalUser getUser(int uid) throws DrupalFetchException {
        try {
            connect();
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("uid", uid);
            if (session != null) {
                data.put("sessid", session);
            }
            String result = drupalServicesRequestManager.postSigned(servicePath, SERVICE_NAME_USER_GET, data, false);
            JSONObject objectResult = new JSONObject(result);
            assertNoErrors(objectResult);

            JSONObject dataObject = objectResult.getJSONObject("#data");
            setSession(dataObject.getString("sessid"));
            DrupalJsonObjectSerializer<DrupalUser> serializer = DrupalJsonObjectSerializerFactory.getInstance(DrupalUser.class);
            user = serializer.unserialize(dataObject.getJSONObject("user").toString());
            return user;
        } catch (IOException e) {
            throw new DrupalFetchException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalFetchException(e);
        } catch (InvalidKeyException e) {
            throw new DrupalFetchException(e);
        } catch (JSONException e) {
            throw new DrupalFetchException(e);
        }
    }

    public List<GenericCookie> getCurrentUserCookie() {
        return cookies;
    }

    public String getFileUploadToken() throws DrupalFetchException {
        try {
            connect();
            Map<String, Object> data = new HashMap<String, Object>();
            if (session != null) {
                data.put("sessid", session);
            }
            String result = drupalServicesRequestManager.postSigned(servicePath, "file.getUploadToken", data, false);
            JSONObject object = new JSONObject(result);
            assertNoErrors(object);
            return object.getString("#data");
        } catch (IOException e) {
            throw new DrupalFetchException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalFetchException(e);
        } catch (InvalidKeyException e) {
            throw new DrupalFetchException(e);
        } catch (JSONException e) {
            throw new DrupalFetchException(e);
        }
    }

    public DrupalFile saveFileStream(InputStream inputStream, String fileName, String token) throws DrupalSaveException {
        try {
            String response = drupalServicesRequestManager.postFile(drupalSiteUrl + "/dandy/fileupload/" + token, "files[upload]", inputStream, fileName);
            if ("0".equals(response)) {
                throw new DrupalSaveException(new Exception("Unable to save file."));
            }
            DrupalJsonObjectSerializer<DrupalFile> serializer = DrupalJsonObjectSerializerFactory.getInstance(DrupalFile.class);
            return serializer.unserialize(response);
        } catch (MalformedURLException e) {
            throw new DrupalSaveException(e);
        } catch (IOException e) {
            throw new DrupalSaveException(e);
        } catch (JSONException e) {
            throw new DrupalSaveException(e);
        } catch (DrupalFetchException e) {
            throw new DrupalSaveException(e);
        }
    }

    void setSession(String session) {
        this.session = session;
    }

    public void setDrupalServicesRequestManager(DrupalServicesRequestManager drupalServicesRequestManager) {
        this.drupalServicesRequestManager = drupalServicesRequestManager;
    }
}
