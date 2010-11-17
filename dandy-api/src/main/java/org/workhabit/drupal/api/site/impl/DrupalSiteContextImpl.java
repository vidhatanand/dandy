package org.workhabit.drupal.api.site.impl;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.workhabit.drupal.api.entity.DrupalComment;
import org.workhabit.drupal.api.entity.DrupalNode;
import org.workhabit.drupal.api.entity.DrupalTaxonomyTerm;
import org.workhabit.drupal.api.entity.DrupalUser;
import org.workhabit.drupal.api.json.DrupalJsonObjectSerializer;
import org.workhabit.drupal.api.site.*;
import org.workhabit.drupal.http.JsonRequestManager;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright 2009 - WorkHabit, Inc. - acs
 * Date: Sep 24, 2010, 12:21:03 PM
 */
public class DrupalSiteContextImpl implements DrupalSiteContext {

    private JsonRequestManager jsonRequestManager;

    private String session;

    private DrupalUser user;
    private boolean isConnected;
    private String drupalSiteUrl;

    /**
     * Constructor takes an authentication token to use for the lifecycle of requests for this instance
     *
     * @param drupalSiteUrl site url to connect to
     */
    public DrupalSiteContextImpl(String drupalSiteUrl) {
        this.drupalSiteUrl = drupalSiteUrl;
    }

    /**
     * call system.connect on the current instance
     */
    public void connect() throws DrupalFetchException {
        if (!isConnected) {
            try {
                String result = jsonRequestManager.post(drupalSiteUrl + "/services/json", "system.connect", null, true);
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
            isConnected = true;
        }
    }

    public void logout() throws DrupalLogoutException {
        try {
            connect();
            String result = jsonRequestManager.postSigned(drupalSiteUrl + "/services/json", "user.logout", null, true);
            JSONObject object = new JSONObject(result);
            if ("true".equals(object.getString("#error"))) {
                throw new DrupalFetchException(object);
            }
            setSession(null);
            isConnected = false;
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

    public List<DrupalNode> getNodeView(String viewName) throws DrupalFetchException {
        return getNodeView(viewName, null);
    }

    protected void assertNoErrors(JSONObject objectResult) throws JSONException, DrupalFetchException {
        if (objectResult.has("#error") && objectResult.getBoolean("#error")) {
            throw new DrupalFetchException(objectResult);
        }
    }

    public DrupalNode getNode(int nid) throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("nid", nid);
        data.put("sessid", session);
        try {
            String result = jsonRequestManager.postSigned(drupalSiteUrl + "/services/json", "node.get", data, true);
            DrupalJsonObjectSerializer<DrupalNode> serializer = new DrupalJsonObjectSerializer<DrupalNode>(DrupalNode.class);
            return serializer.unserialize(result);
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    public DrupalComment getComment(int cid) throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("cid", cid);
        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }

        try {
            String result = jsonRequestManager.postSigned(drupalSiteUrl + "/services/json", "node.get", data, true);
            DrupalJsonObjectSerializer<DrupalComment> serializer = new DrupalJsonObjectSerializer<DrupalComment>(DrupalComment.class);
            return serializer.unserialize(result);
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    public void saveComment(final DrupalComment comment) throws DrupalFetchException {
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
        data.put("comment", jsonComment);
        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }

        try {
            String response = jsonRequestManager.postSigned(drupalSiteUrl + "/services/json", "comment.save", data, false);
            System.out.println(response);
        } catch (NoSuchAlgorithmException e) {
            throw new DrupalSaveException(e);
        } catch (IOException e) {
            throw new DrupalSaveException(e);
        } catch (InvalidKeyException e) {
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
            String result = jsonRequestManager.postSigned(drupalSiteUrl + "/services/json", "user.login", data, true);
            return processLoginResult(result);
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    public List<DrupalTaxonomyTerm> getTermView(String viewName) throws DrupalFetchException {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("view_name", viewName);
        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }

        try {
            String result = jsonRequestManager.postSigned(drupalSiteUrl + "/services/json", "views.get", data, true);
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
            String result = jsonRequestManager.postSigned(drupalSiteUrl + "/services/json", "taxonomy.dictionary", data, true);
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

    public DrupalUser registerNewUser(String username, String password, String email) {
        // TODO: Implement
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<DrupalNode> getNodeView(String viewName, String viewArguments) throws DrupalFetchException {
        connect();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("view_name", viewName);
        if (viewArguments != null) {
            data.put("args", viewArguments);
        }
        if (session != null && !"".equals(session)) {
            data.put("sessid", session);
        }
        try {
            String result = jsonRequestManager.postSigned(drupalSiteUrl + "/services/json", "views.get", data, true);
            DrupalJsonObjectSerializer<DrupalNode> serializer = new DrupalJsonObjectSerializer<DrupalNode>(DrupalNode.class);
            return serializer.unserializeList(result);
        } catch (Exception e) {
            throw new DrupalFetchException(e);
        }
    }

    private List<DrupalTaxonomyTerm> processGetTermViewResult(String result) throws DrupalFetchException, JSONException {
        DrupalJsonObjectSerializer<DrupalTaxonomyTerm> serializer = new DrupalJsonObjectSerializer<DrupalTaxonomyTerm>(DrupalTaxonomyTerm.class);
        return serializer.unserializeList(result);
    }

    private DrupalUser processLoginResult(String result) throws JSONException, DrupalFetchException, DrupalLoginException {
        JSONObject objectResult = new JSONObject(result);
        assertNoErrors(objectResult);

        JSONObject dataObject = objectResult.getJSONObject("#data");
        setSession(dataObject.getString("sessid"));
        user = new DrupalUser(dataObject.getJSONObject("user"), drupalSiteUrl);
        return user;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public void setJsonRequestManager(JsonRequestManager jsonRequestManager) {
        this.jsonRequestManager = jsonRequestManager;
    }

}