/* Generated By:JJTree: Do not edit this line. OExistsSystemUserStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OServerCommandContext;
import com.orientechnologies.orient.core.db.OSystemDatabase;
import com.orientechnologies.orient.core.sql.executor.OInternalResultSet;
import com.orientechnologies.orient.core.sql.executor.OResultInternal;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OExistsSystemUserStatement extends OSimpleExecServerStatement {

  protected OIdentifier name;

  protected OInputParameter nameParam;

  public OExistsSystemUserStatement(int id) {
    super(id);
  }

  public OExistsSystemUserStatement(OrientSql p, int id) {
    super(p, id);
  }

  @Override
  public OResultSet executeSimple(OServerCommandContext ctx) {

    OSystemDatabase systemDb = ctx.getServer().getSystemDatabase();

    OResultInternal result = new OResultInternal();
    result.setProperty("operation", "exists system user");
    if (name != null) {
      result.setProperty("name", name.getStringValue());
    } else {
      result.setProperty("name", nameParam.getValue(ctx.getInputParameters()));
    }

    systemDb.executeWithDB(
        (db) -> {
          List<Object> params = new ArrayList<>();
          if (name != null) {
            params.add(name.getStringValue());
          } else {
            params.add(nameParam.getValue(ctx.getInputParameters()));
          }
          // INSERT INTO OUser SET
          StringBuilder sb = new StringBuilder();
          sb.append("SELECT FROM OUser WHERE name = ?");

          try (OResultSet rs = db.command(sb.toString(), params.toArray())) {
            if (rs.hasNext()) {
              result.setProperty("exists", true);
            } else {
              result.setProperty("exists", false);
            }
          }
          return null;
        });

    OInternalResultSet rs = new OInternalResultSet();
    rs.add(result);
    return rs;
  }

  @Override
  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("EXISTS SYSTEM USER ");
    if (name != null) {
      name.toString(params, builder);
    } else {
      nameParam.toString(params, builder);
    }
  }

  @Override
  public OExistsSystemUserStatement copy() {
    OExistsSystemUserStatement result = new OExistsSystemUserStatement(-1);
    result.name = name == null ? null : name.copy();
    result.nameParam = nameParam == null ? null : nameParam.copy();
    return result;
  }
}
/* JavaCC - OriginalChecksum=6df1219621900cc168a7a1d8bef2fa31 (do not edit this line) */