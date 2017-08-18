/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jaeger.tracing.addon.util;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

/**
 * Helper to create source files from Freemarker templates.
 * Templates are expected to live in src/main/resources/templates in the source code.
 * @author hrupp
 */
public class WriteClassHelper {

  public static JavaClassSource writeClassFromTemplate(String packageName, String templateName, Map configItems, PrintStream out) {
    try {

      Configuration config = new Configuration();
      File dir = new File("src/main/resources/");
      if (!dir.exists()) {
        out.println("DEBUG: src/main/resources not found");
        dir = new File("templates");
      }
      if (!dir.exists()) {
        out.println("DEBUG: Trying via classloader");
        config.setDirectoryForTemplateLoading(dir);
      }
      else {
        config.setClassForTemplateLoading(WriteClassHelper.class,"/");
      }

      out.println("INFO: Applying template " + templateName);
      Template controllerTemplate = config.getTemplate("templates/"+ templateName);
      Writer contents = new StringWriter();
      controllerTemplate.process(configItems, contents);
      contents.flush();
      JavaClassSource resource = Roaster.parse(JavaClassSource.class, contents.toString());
      resource.setPackage(packageName);

      return resource;
    } catch (IOException | TemplateException e) {
      throw new RuntimeException(e);
    }
  }

}

