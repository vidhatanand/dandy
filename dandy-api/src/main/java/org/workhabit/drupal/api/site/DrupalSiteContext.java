package org.workhabit.drupal.api.site;

import org.workhabit.drupal.api.entity.DrupalComment;
import org.workhabit.drupal.api.entity.DrupalNode;
import org.workhabit.drupal.api.entity.DrupalTaxonomyTerm;
import org.workhabit.drupal.api.entity.DrupalUser;
import org.workhabit.drupal.api.site.exceptions.DrupalFetchException;
import org.workhabit.drupal.api.site.exceptions.DrupalLoginException;
import org.workhabit.drupal.api.site.exceptions.DrupalLogoutException;
import org.workhabit.drupal.api.site.exceptions.DrupalSaveException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Copyright 2009 - WorkHabit, Inc. - acs
 * Date: Oct 11, 2010, 5:10:31 PM
 */
@SuppressWarnings({"UnusedDeclaration"})
public interface DrupalSiteContext {
    void connect() throws DrupalFetchException;

    void logout() throws DrupalLogoutException;

    List<DrupalNode> getNodeView(String viewName) throws DrupalFetchException;

    List<DrupalNode> getNodeView(String viewName, String viewArguments) throws DrupalFetchException;

    DrupalNode getNode(int nid) throws DrupalFetchException;

    DrupalComment getComment(int cid) throws DrupalFetchException;

    void saveComment(DrupalComment comment) throws DrupalFetchException;

    DrupalUser login(String username, String password) throws DrupalLoginException, DrupalFetchException;

    List<DrupalTaxonomyTerm> getTermView(String s) throws DrupalFetchException;

    List<DrupalTaxonomyTerm> getCategoryList() throws DrupalFetchException;

    int registerNewUser(String username, String password, String email) throws DrupalSaveException;

    int saveFile(byte[] bytes, String fileName) throws DrupalFetchException;

    List<DrupalComment> getComments(int nid) throws DrupalFetchException;

    InputStream getFileStream(String filepath) throws IOException;

    String getFileDirectoryPath() throws DrupalFetchException;

    int saveNode(DrupalNode node) throws DrupalSaveException;
}
