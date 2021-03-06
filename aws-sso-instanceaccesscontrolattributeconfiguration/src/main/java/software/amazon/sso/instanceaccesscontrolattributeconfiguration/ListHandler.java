package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Currently allowed single InstanceAccessControlAttributeConfiguration instance per SSO
 * To get current configuration please use READ handler
 */
public class ListHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<SsoAdminClient> proxyClient,
            final Logger logger) {

        final List<ResourceModel> models = new ArrayList<>();
        models.add(request.getDesiredResourceState());

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModels(models)
                .status(OperationStatus.SUCCESS)
                .build();
    }
}
