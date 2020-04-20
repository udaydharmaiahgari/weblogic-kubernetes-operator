<%-- Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates. --%>
<%-- Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl. --%>
<%@ page import="javax.naming.InitialContext" %>
<%@ page import="javax.management.*" %>
<%@ page import="java.io.*" %>
<%
  try {
    String srName=System.getProperty("weblogic.Name");

    out.println("**************************************************************");
    out.println();
    out.println("Hello World! Welcome to WebLogic server '" + srName + "'!");

    InitialContext ic = new InitialContext();

    MBeanServer mbs = (MBeanServer)ic.lookup("java:comp/env/jmx/runtime");

    ObjectName jvmRuntime  = new ObjectName("com.bea:ServerRuntime=" + srName + ",Name=" + srName + ",Type=JVMRuntime");
    ObjectName jdbcRuntime = new ObjectName("com.bea:ServerRuntime=" + srName + ",Name=" + srName + ",Type=JDBCServiceRuntime");
    ObjectName[] dataSources = (ObjectName[])mbs.getAttribute(jdbcRuntime, "JDBCDataSourceRuntimeMBeans");

    out.println();
    out.println("Free JVM Heap " + mbs.getAttribute(jvmRuntime, "HeapFreePercent") + "%.");
    out.println();
    out.println("Found " + dataSources.length + " data source" + (String)((dataSources.length!=1)?"s:":":"));

    for (ObjectName dataSource : dataSources) {
       String dsName  = (String)mbs.getAttribute(dataSource, "Name");
       String dsState = (String)mbs.getAttribute(dataSource, "State");
       out.println("  Datasource '" + dsName + "': State='" + dsState +"'");
    }

    out.println();
    out.println("**************************************************************");

  } catch (Throwable t) {
    t.printStackTrace(new PrintStream(response.getOutputStream()));
  }
%>
