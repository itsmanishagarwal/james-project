/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.webadmin.routes;

import static spark.Spark.halt;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.MailAddress;
import org.apache.james.rrt.api.MappingAlreadyExistsException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.SameSourceAndDestinationException;
import org.apache.james.rrt.api.SourceDomainIsNotInDomainListException;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;


@Api(tags = "AddressMappings")
@Path(MappingRoutes.BASE_PATH)
@Produces(Constants.JSON_CONTENT_TYPE)
public class AddressMappingRoutes implements Routes {

    static final String BASE_PATH = "/mappings/address/";
    static final String ADD_ADDRESS_MAPPING_PATH = "/mappings/address/:mappingSource/targets/:destinationAddress";

    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    AddressMappingRoutes(RecipientRewriteTable recipientRewriteTable) {
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(ADD_ADDRESS_MAPPING_PATH, this::addAddressMapping);
    }

    @POST
    @Path(ADD_ADDRESS_MAPPING_PATH)
    @ApiOperation(value = "Getting all user mappings in RecipientRewriteTable")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "No body on created", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid parameter values.")
    })
    public HaltException addAddressMapping(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress source = MailAddressParser.parseMailAddress(
            request.params("mappingSource"),"address");
        MailAddress destinationAddress = MailAddressParser.parseMailAddress(
            request.params("destinationAddress"), "address");
        addAddressMapping(MappingSource.fromMailAddress(source), destinationAddress);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    private void addAddressMapping(MappingSource source, MailAddress destination) throws RecipientRewriteTableException {
        try {
            recipientRewriteTable.addAddressMapping(source, destination.asString());
        } catch (MappingAlreadyExistsException e) {
            // ignore
        } catch (SameSourceAndDestinationException | SourceDomainIsNotInDomainListException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .haltError();
        }
    }

}
