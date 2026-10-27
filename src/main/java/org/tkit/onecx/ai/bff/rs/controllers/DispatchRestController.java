package org.tkit.onecx.ai.bff.rs.controllers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.tkit.onecx.ai.bff.rs.mappers.DispatchMapper;
import org.tkit.onecx.ai.bff.rs.mappers.ExceptionMapper;
import org.tkit.quarkus.log.cdi.LogService;

import gen.org.tkit.onecx.ai.management.bff.client.api.DispatchInternalApi;
import gen.org.tkit.onecx.ai.management.bff.client.model.ChatMessageInternal;
import gen.org.tkit.onecx.ai.management.bff.client.model.ProblemDetailResponseInternal;
import gen.org.tkit.onecx.ai.management.bff.rs.internal.DispatchApiService;
import gen.org.tkit.onecx.ai.management.bff.rs.internal.model.ChatRequestDTO;
import gen.org.tkit.onecx.ai.management.bff.rs.internal.model.ProblemDetailResponseDTO;

@ApplicationScoped
@Transactional(value = Transactional.TxType.NOT_SUPPORTED)
@LogService
public class DispatchRestController implements DispatchApiService {
    @Inject
    @RestClient
    DispatchInternalApi dispatchInternalApi;

    @Inject
    DispatchMapper dispatchMapper;

    @Inject
    ExceptionMapper exceptionMapper;

    @Override
    public Response chat(ChatRequestDTO chatRequestDTO) {
        var chatRequest = dispatchMapper.map(chatRequestDTO);
        try (Response response = dispatchInternalApi.chat(chatRequest)) {
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                return dispatchFailure(response);
            }
            var message = response.readEntity(ChatMessageInternal.class);
            return Response.status(response.getStatus()).entity(dispatchMapper.map(message)).build();
        } catch (ClientWebApplicationException ex) {
            // the REST client throws for non-2xx responses
            return dispatchFailure(ex.getResponse());
        }
    }

    /**
     * Forwards the SVC status and its ProblemDetail error code so the UI can distinguish failure reasons.
     * A body that is not a ProblemDetail is dropped and only the status is returned.
     */
    private Response dispatchFailure(Response response) {
        var status = response.getStatus();
        if (response.getMediaType() != null && response.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            try {
                var problem = response.readEntity(ProblemDetailResponseInternal.class);
                if (problem != null && problem.getErrorCode() != null) {
                    return Response.status(status)
                            .type(MediaType.APPLICATION_JSON)
                            .entity(exceptionMapper.exception(problem.getErrorCode(), problem.getDetail()))
                            .build();
                }
            } catch (ProcessingException ignored) {
                // body is not a valid ProblemDetail, fall back to status only
            }
        }
        return Response.status(status).build();
    }

    @ServerExceptionMapper
    public Response restException(ClientWebApplicationException ex) {
        return exceptionMapper.clientException(ex);
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> constraintException(ConstraintViolationException ex) {
        return exceptionMapper.constraint(ex);
    }
}
