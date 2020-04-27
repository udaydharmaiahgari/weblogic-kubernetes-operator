// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public interface TestConstants {

  // operator constants
  public static final String OPERATOR_RELEASE_NAME = "weblogic-operator";
  public static final String OPERATOR_CHART_DIR =
      "../kubernetes/charts/weblogic-operator";
  public static final String IMAGE_NAME_OPERATOR =
      "oracle/weblogic-kubernetes-operator";
  public static final String OPERATOR_DOCKER_BUILD_SCRIPT =
      "../buildDockerImage.sh";
  public static final String REPO_NAME = "phx.ocir.io/weblogick8s/";
  public static final String REPO_DUMMY_VALUE = "dummy";
  public static final String REPO_SECRET_NAME = "ocir-secret";
  public static final String REPO_REGISTRY = Optional.ofNullable(System.getenv("REPO_REGISTRY"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String REPO_USERNAME = Optional.ofNullable(System.getenv("REPO_USERNAME"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String REPO_PASSWORD = Optional.ofNullable(System.getenv("REPO_PASSWORD"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String REPO_EMAIL = Optional.ofNullable(System.getenv("REPO_EMAIL"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String BUILD_ID = Optional.ofNullable(System.getenv("BUILD_ID"))
      .orElse("");
  public static final String BRANCH_NAME_FROM_JENKINS = Optional.ofNullable(System.getenv("BRANCH"))
      .orElse("");
  public static final String K8S_NODEPORT_HOST = Optional.ofNullable(System.getenv("K8S_NODEPORT_HOST"))
        .orElse(assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostName()));
  public static final String GOOGLE_REPO_URL = "https://kubernetes-charts.storage.googleapis.com/";
  public static final String PROJECT_ROOT = System.getProperty("user.dir");
  public static final String TRAEFIK_SAMPLE_VALUE_FILE =
      PROJECT_ROOT + "/../kubernetes/samples/charts/traefik/values.yaml";
  public static final String INGRESS_SAMPLE_CHART_DIR =
      PROJECT_ROOT + "/../kubernetes/samples/charts/ingress-per-domain";
}