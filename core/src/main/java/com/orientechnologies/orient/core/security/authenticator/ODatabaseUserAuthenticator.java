package com.orientechnologies.orient.core.security.authenticator;

import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.exception.OSecurityAccessException;
import com.orientechnologies.orient.core.metadata.security.OSecurityShared;
import com.orientechnologies.orient.core.metadata.security.OSecurityUser;
import com.orientechnologies.orient.core.metadata.security.OUser;
import com.orientechnologies.orient.core.security.OGlobalUser;

public class ODatabaseUserAuthenticator extends OSecurityAuthenticatorAbstract {

  @Override
  public String authenticate(ODatabaseSession session, String username, String password) {
    if (session == null) {
      return null;
    }

    String dbName = session.getName();
    OSecurityShared databaseSecurity =
        (OSecurityShared) ((ODatabaseDocumentInternal) session).getSharedContext().getSecurity();
    OUser user = databaseSecurity.getUserInternal(session, username);

    if (user.getAccountStatus() != OSecurityUser.STATUSES.ACTIVE)
      throw new OSecurityAccessException(dbName, "User '" + username + "' is not active");

    // CHECK USER & PASSWORD
    if (!user.checkPassword(password)) {
      // WAIT A BIT TO AVOID BRUTE FORCE
      try {
        Thread.sleep(200);
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
      throw new OSecurityAccessException(
          dbName, "User or password not valid for database: '" + dbName + "'");
    }

    return user.getName();
  }

  @Override
  public OGlobalUser getUser(String username) {
    return null;
  }

  @Override
  public boolean isAuthorized(String username, String resource) {
    return false;
  }

  @Override
  public boolean isSingleSignOnSupported() {
    return false;
  }
}