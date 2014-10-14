/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.commons.jdbi.template;

import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizer;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizingAnnotation;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.StringTemplate3StatementLocator;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;
import org.skife.jdbi.v2.tweak.StatementLocator;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;


@SqlStatementCustomizingAnnotation(KillBillSqlDaoStringTemplate.KillBillSqlDaoStringTemplateFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface KillBillSqlDaoStringTemplate {
    static final String DEFAULT_VALUE = " ~ ";

    String value() default DEFAULT_VALUE;

    public static class KillBillSqlDaoStringTemplateFactory<SqlDao, ModelDao> extends UseStringTemplate3StatementLocator.LocatorFactory {

        static ConcurrentMap<String, StatementLocator> locatorCache = new ConcurrentHashMap<String, StatementLocator>();


        //
        // This is only needed to compute the key for the cache -- whether we get a class or a pathname (string)
        //
        // (Similar to what jdbi is doing (StringTemplate3StatementLocator))
        //
        private final static String sep = "/"; // *Not* System.getProperty("file.separator"), which breaks in jars

        public static String mungify(final Class claz) {
            final String path = "/" + claz.getName();
            return path.replaceAll("\\.", Matcher.quoteReplacement(sep)) + ".sql.stg";
        }


        private StatementLocator getLocator(final String locatorPath) {

            if (locatorCache.containsKey(locatorPath)) {
                return locatorCache.get(locatorPath);
            }

            // A bit of java magic to extract parameterizedType
            final ParameterizedType parameterizedType =
                    (ParameterizedType) getClass().getGenericSuperclass();


            final StringTemplate3StatementLocator.Builder builder = StringTemplate3StatementLocator.builder(locatorPath)
                    .shouldCache()
                    .withSuperGroup((Class) parameterizedType.getActualTypeArguments()[0])
                    .allowImplicitTemplateGroup()
                    .treatLiteralsAsTemplates();

            final StatementLocator locator = builder.build();
            locatorCache.put(locatorPath, locator);
            return locator;
        }


        public SqlStatementCustomizer createForType(final Annotation annotation, final Class sqlObjectType) {

            final KillBillSqlDaoStringTemplate a = (KillBillSqlDaoStringTemplate) annotation;

            // A bit of java magic to extract parameterizedType
            final ParameterizedType parameterizedType =
                    (ParameterizedType) getClass().getGenericSuperclass();

            final String locatorPath = DEFAULT_VALUE.equals(a.value()) ? mungify(sqlObjectType) : a.value();
            final StatementLocator l = getLocator(locatorPath);
            return new SqlStatementCustomizer() {
                public void apply(final SQLStatement statement) {
                    statement.setStatementLocator(l);

                    if (statement instanceof Query) {
                        final Query query = (Query) statement;

                        // Find the model class associated with this sqlObjectType (which is a SqlDao class) to register its mapper
                        // If a custom mapper is defined via @RegisterMapper, don't register our generic one
                        if (sqlObjectType.getGenericInterfaces() != null &&
                                sqlObjectType.getAnnotation(RegisterMapper.class) == null) {
                            for (int i = 0; i < sqlObjectType.getGenericInterfaces().length; i++) {
                                if (sqlObjectType.getGenericInterfaces()[i] instanceof ParameterizedType) {
                                    final ParameterizedType type = (ParameterizedType) sqlObjectType.getGenericInterfaces()[i];
                                    for (int j = 0; j < type.getActualTypeArguments().length; j++) {
                                        final Type modelType = type.getActualTypeArguments()[j];
                                        if (modelType instanceof Class) {
                                            final Class modelClazz = (Class) modelType;
                                            final Class modelDaoClass = (Class) parameterizedType.getActualTypeArguments()[1];
                                            if (modelDaoClass.isAssignableFrom(modelClazz)) {
                                                query.registerMapper(new LowerToCamelBeanMapperFactory(modelClazz));
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            };
        }

        public SqlStatementCustomizer createForMethod(final Annotation annotation,
                                                      final Class sqlObjectType,
                                                      final Method method) {
            throw new UnsupportedOperationException("Not Defined on Method");
        }

        public SqlStatementCustomizer createForParameter(final Annotation annotation,
                                                         final Class sqlObjectType,
                                                         final Method method,
                                                         final Object arg) {
            throw new UnsupportedOperationException("Not defined on parameter");
        }
    }
}
