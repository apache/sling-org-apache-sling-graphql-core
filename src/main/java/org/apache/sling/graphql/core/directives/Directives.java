/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.graphql.core.directives;

import graphql.introspection.Introspection;
import graphql.language.Description;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.InputValueDefinition;
import graphql.language.NonNullType;
import graphql.language.StringValue;
import graphql.language.TypeName;

public class Directives {

    private Directives() {}
    
    public static final String TYPE_STRING = "String";

    public static final DirectiveDefinition CONNECTION = DirectiveDefinition.newDirectiveDefinition()
            .name("connection")
            .directiveLocation(DirectiveLocation.newDirectiveLocation().name(Introspection.DirectiveLocation.FIELD_DEFINITION.name()).build())
            .description(new Description("Marks a connection type according to the Relay specification.", null, false))
            .inputValueDefinition(
                    InputValueDefinition.newInputValueDefinition()
                            .name("for")
                            .description(new Description("The type for which the connection is created.", null, false))
                            .type(NonNullType.newNonNullType(TypeName.newTypeName(TYPE_STRING).build()).build())
                            .build()
            )
            .build();

    public static final DirectiveDefinition FETCHER = DirectiveDefinition.newDirectiveDefinition()
            .name("fetcher")
            .directiveLocation(DirectiveLocation.newDirectiveLocation().name(Introspection.DirectiveLocation.FIELD_DEFINITION.name()).build())
            .description(new Description("Maps a field to a SlingDataDetcher.", null, false))
            .inputValueDefinition(
                    InputValueDefinition.newInputValueDefinition()
                            .name("name")
                            .description(new Description("The name with which the SlingDataFetcher was registered.", null, false))
                            .type(NonNullType.newNonNullType(TypeName.newTypeName(TYPE_STRING).build()).build())
                            .build()
            )
            .inputValueDefinition(
                    InputValueDefinition.newInputValueDefinition()
                            .name("options")
                            .description(new Description("Options passed to the SlingDataFetcher.", null, false))
                            .type(TypeName.newTypeName(TYPE_STRING).build())
                            .defaultValue(new StringValue(""))
                            .build()
            )
            .inputValueDefinition(
                    InputValueDefinition.newInputValueDefinition()
                            .name("source")
                            .description(new Description("Source information passed to the SlingDataFetcher.", null, false))
                            .type(TypeName.newTypeName(TYPE_STRING).build())
                            .defaultValue(new StringValue(""))
                            .build()
            )
            .build();

    public static final DirectiveDefinition RESOLVER = DirectiveDefinition.newDirectiveDefinition()
            .name("resolver")
            .directiveLocation(DirectiveLocation.newDirectiveLocation().name(Introspection.DirectiveLocation.INTERFACE.name()).build())
            .directiveLocation(DirectiveLocation.newDirectiveLocation().name(Introspection.DirectiveLocation.UNION.name()).build())
            .description(new Description("Maps a type to a SlingTypeResolver.", null, false))
            .inputValueDefinition(
                    InputValueDefinition.newInputValueDefinition()
                            .name("name")
                            .description(new Description("The name with which the SlingTypeResolver was registered.", null, false))
                            .type(NonNullType.newNonNullType(TypeName.newTypeName(TYPE_STRING).build()).build())
                            .build()
            )
            .inputValueDefinition(
                    InputValueDefinition.newInputValueDefinition()
                            .name("options")
                            .description(new Description("Options passed to the SlingTypeResolver.", null, false))
                            .type(TypeName.newTypeName(TYPE_STRING).build())
                            .defaultValue(new StringValue(""))
                            .build()
            )
            .inputValueDefinition(
                    InputValueDefinition.newInputValueDefinition()
                            .name("source")
                            .description(new Description("Source information passed to the SlingTypeResolver.", null, false))
                            .type(TypeName.newTypeName(TYPE_STRING).build())
                            .defaultValue(new StringValue(""))
                            .build()
            )
            .build();
}
