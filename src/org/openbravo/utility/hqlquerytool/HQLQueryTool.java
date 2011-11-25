/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html 
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License. 
 * The Original Code is Openbravo ERP. 
 * The Initial Developer of the Original Code is Openbravo SLU 
 * All portions are Copyright (C) 2009 Openbravo SLU 
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.utility.hqlquerytool;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.Query;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.erpCommon.businessUtility.WindowTabs;
import org.openbravo.erpCommon.utility.LeftTabsBar;
import org.openbravo.erpCommon.utility.NavigationBar;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.ToolBar;
import org.openbravo.xmlEngine.XmlDocument;

/**
 * Is a copy of the new HQTLSimpleFieldProvider class which will be present in 2.50-MP7. This class
 * has been added to this module to make it possible to use this module in older versions of
 * Openbravo 2.50.
 * 
 * @author mtaal
 */

public class HQLQueryTool extends HttpSecureAppServlet {
  private static final long serialVersionUID = 1L;

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException,
      ServletException {

    // Commands:
    // DEFAULT
    // RESULT --> query result
    // ENTITY --> entity property list
    final VariablesSecureApp vars = new VariablesSecureApp(request);

    if (vars.commandIn("DEFAULT")) {
      printPage(response, vars, null);
    } else if (vars.commandIn("RESULT")) {
      response.setContentType("text/plain; charset=UTF-8");
      response.setHeader("Cache-Control", "no-cache");
      PrintWriter out = response.getWriter();
      final String result = getResult(vars);
      out.print(result);
      out.close();
    } else if (vars.commandIn("PROPERTYLIST")) {
      response.setHeader("Cache-Control", "no-cache");
      PrintWriter out = response.getWriter();
      final String propertyList = printPropertyList(vars);
      out.print(propertyList);
      out.close();
    } else {
      pageError(response);
    }
  }

  private String printPropertyList(VariablesSecureApp vars) {
    final String entityName = vars.getStringParameter("inpEntity");
    if (entityName == null || entityName.trim().length() == 0) {
      return "";
    }
    final Entity entity = ModelProvider.getInstance().getEntity(entityName);

    final String xmlTemplateName = "org/openbravo/utility/hqlquerytool/HQLQueryTool_PropertyList";
    final XmlDocument xmlDocument = xmlEngine.readXmlTemplate(xmlTemplateName).createXmlDocument();

    xmlDocument.setParameter("entityName", entity.getName());

    final List<FieldProvider> result = new ArrayList<FieldProvider>();

    final List<String> props = new ArrayList<String>();
    for (Property p : entity.getProperties()) {
      props.add(p.getName());
    }
    Collections.sort(props);

    for (String propName : props) {
      final HQLSimpleFieldProvider fp = new HQLSimpleFieldProvider();
      fp.setField("result", propName);
      result.add(fp);
    }

    xmlDocument.setData("lines", result.toArray(new FieldProvider[result.size()]));
    return xmlDocument.print();
  }

  private String getResult(VariablesSecureApp vars) {
    final String queryStr = vars.getStringParameter("inpHQLQuery");
    final org.hibernate.Session session = OBDal.getInstance().getSession();
    try {
      if (!OBContext.getOBContext().isInAdministratorMode()) {
        throw new IllegalStateException(
            "Only users with the System Administrator role can use this tool");
      }
      final Query qry = session.createQuery(queryStr);
      qry.setMaxResults(10000);
      final List<FieldProvider> result = new ArrayList<FieldProvider>();
      int cnt = 0;
      for (Object resultObject : qry.list()) {
        final StringBuilder resultLine = new StringBuilder();
        if (resultObject.getClass().isArray()) {
          final Object[] values = (Object[]) resultObject;
          for (Object value : values) {
            if (resultLine.length() > 0) {
              resultLine.append(", ");
            }
            resultLine.append(printObject(value, false));
          }
        } else {
          resultLine.append(printObject(resultObject, true));
        }
        final HQLSimpleFieldProvider fp = new HQLSimpleFieldProvider();
        fp.setField("num", "" + ++cnt);
        fp.setField("result", resultLine.toString());
        result.add(fp);
      }

      final String xmlTemplateName = "org/openbravo/utility/hqlquerytool/HQLQueryTool_Result";
      final XmlDocument xmlDocument = xmlEngine.readXmlTemplate(xmlTemplateName)
          .createXmlDocument();
      xmlDocument.setData("lines", result.toArray(new FieldProvider[result.size()]));

      return xmlDocument.print();

    } catch (Throwable t) {
      return getErrorResult(vars, t);
    }
  }

  private String printObject(Object value, boolean complete) {
    if (value == null) {
      return "NULL";
    }
    if (value instanceof BaseOBObject) {
      final BaseOBObject bob = (BaseOBObject) value;
      if (complete) {
        return printBaseOBObject(bob);
      } else {
        return getEntityLink(bob, bob.getId() + " [identifier: " + bob.getIdentifier()
            + ", entity: " + bob.getEntityName() + "]");
      }
    }
    return value.toString();
  }

  private String printBaseOBObject(BaseOBObject bob) {
    final boolean derivedReadable = OBContext.getOBContext().getEntityAccessChecker()
        .isDerivedReadable(bob.getEntity());
    if (derivedReadable) {
      // only prints the identifier
      return bob.toString();
    }
    final StringBuilder sb = new StringBuilder();
    sb.append("[entity: " + bob.getEntityName());
    // and also display all values
    for (final Property p : bob.getEntity().getProperties()) {
      if (p.isOneToMany()) {
        continue;
      }
      Object value = bob.get(p.getName());
      if (value != null) {
        sb.append(", ");
        if (value instanceof BaseOBObject) {
          final BaseOBObject bobValue = (BaseOBObject) value;
          value = getEntityLink(bobValue, bobValue.getId() + " (" + bobValue.getIdentifier() + ")");
        } else if (p.isId()) {
          value = getEntityLink(bob, (String) value);
        }
        sb.append(p.getName() + ": " + value);
      }
    }
    sb.append("]");
    return sb.toString();
  }

  private String getEntityLink(BaseOBObject bob, String title) {
    return "<a target='_new' href='../ws/dal/" + bob.getEntityName() + "/" + bob.getId()
        + "?template=bo.xslt'>" + title + "</a>";
  }

  private String getErrorResult(VariablesSecureApp vars, Throwable t) {

    final StringBuilder sb = new StringBuilder();
    Throwable throwable = t;
    final List<Throwable> throwables = new ArrayList<Throwable>();
    while (throwable != null) {
      throwables.add(throwable);
      final StringWriter sw = new StringWriter();
      final PrintWriter pw = new PrintWriter(sw);
      throwable.printStackTrace(pw);
      final String stackTrace = sw.toString().replaceAll("\\n", "<br/>");
      if (sb.length() > 0) {
        sb.append("<br/>");
      }
      sb.append(stackTrace);
      throwable = throwable.getCause();
      if (throwables.contains(throwables)) {
        break;
      }
    }

    final String xmlTemplateName = "org/openbravo/utility/hqlquerytool/HQLQueryTool_Error";
    final XmlDocument xmlDocument = xmlEngine.readXmlTemplate(xmlTemplateName).createXmlDocument();
    xmlDocument.setParameter("errorResult", sb.toString());

    xmlDocument.setParameter("messageType", "Error");
    xmlDocument.setParameter("messageTitle", "Exception occured");
    xmlDocument.setParameter("messageMessage", t.getMessage());

    return xmlDocument.print();
  }

  private void printPage(HttpServletResponse response, VariablesSecureApp vars, OBError obError)
      throws IOException, ServletException {
    String className = this.getClass().getName().toString();
    String xmlTemplateName = "org/openbravo/utility/hqlquerytool/HQLQueryTool";
    if (log4j.isDebugEnabled())
      log4j.debug("Output: dataSheet");
    XmlDocument xmlDocument = null;
    xmlDocument = xmlEngine.readXmlTemplate(xmlTemplateName).createXmlDocument();

    ToolBar toolbar = new ToolBar(this, vars.getLanguage(),
        "org.openbravo.utility.hqlquerytool.HQLQueryTool", false, "", "", "", false, "ad_forms",
        strReplaceWith, false, true);
    toolbar.prepareSimpleToolBarTemplate();
    xmlDocument.setParameter("toolbar", toolbar.toString());

    try {
      WindowTabs tabs = new WindowTabs(this, vars, className);
      xmlDocument.setParameter("parentTabContainer", tabs.parentTabs());
      xmlDocument.setParameter("mainTabContainer", tabs.mainTabs());
      xmlDocument.setParameter("childTabContainer", tabs.childTabs());
      xmlDocument.setParameter("theme", vars.getTheme());
      NavigationBar nav = new NavigationBar(this, vars.getLanguage(),
          "org.openbravo.utility.hqlquerytool.HQLQueryTool.html", classInfo.id, classInfo.type,
          strReplaceWith, tabs.breadcrumb());
      xmlDocument.setParameter("navigationBar", nav.toString());
      LeftTabsBar lBar = new LeftTabsBar(this, vars.getLanguage(),
          "org.openbravo.utility.hqlquerytool.HQLQueryTool.html", strReplaceWith);
      xmlDocument.setParameter("leftTabs", lBar.manualTemplate());
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
    if (obError != null) {
      xmlDocument.setParameter("messageType", obError.getType());
      xmlDocument.setParameter("messageTitle", obError.getTitle());
      xmlDocument.setParameter("messageMessage", obError.getMessage());
    }

    xmlDocument.setParameter("calendar", vars.getLanguage().substring(0, 2));
    xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
    xmlDocument.setParameter("directory", "var baseDirectory = \"" + strReplaceWith + "/\";");

    xmlDocument.setData("entityOptions", "liststructure", getEntityFieldProviders());

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(xmlDocument.print());
    out.close();
  }

  private FieldProvider[] getEntityFieldProviders() {
    final List<FieldProvider> fieldProviders = new ArrayList<FieldProvider>();

    final HQLSimpleFieldProvider fpEmpty = new HQLSimpleFieldProvider();
    fpEmpty.setField("id", "");
    fpEmpty.setField("name", "");
    fieldProviders.add(fpEmpty);

    for (Entity entity : ModelProvider.getInstance().getModel()) {
      final HQLSimpleFieldProvider fp = new HQLSimpleFieldProvider();
      fp.setField("id", entity.getName());
      fp.setField("name", entity.getTableName() + " - " + entity.getName());
      fieldProviders.add(fp);
    }
    Collections.sort(fieldProviders, new FieldProviderComparator());
    return fieldProviders.toArray(new FieldProvider[fieldProviders.size()]);
  }

  public String getServletInfo() {
    return "Servlet DalQueryTool.";
  } // end of getServletInfo() method

  private class FieldProviderComparator implements Comparator<FieldProvider> {

    @Override
    public int compare(FieldProvider o1, FieldProvider o2) {
      final String v1 = o1.getField("name");
      final String v2 = o2.getField("name");
      if (v1 == v2) {
        return 0;
      } else if (v1 == null) {
        return -1;
      } else if (v2 == null) {
        return 1;
      }
      return v1.compareTo(v2);
    }

  }

}
