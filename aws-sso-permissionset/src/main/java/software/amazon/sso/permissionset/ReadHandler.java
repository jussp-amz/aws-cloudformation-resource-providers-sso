package software.amazon.sso.permissionset;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.sso.permissionset.actionProxy.InlinePolicyProxy;
import software.amazon.sso.permissionset.actionProxy.ManagedPolicyAttachmentProxy;
import java.util.List;

import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS;
import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS_ZERO;
import static software.amazon.sso.permissionset.utils.TagsUtil.getResourceTags;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;
    private List<Tag> tags;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<SsoAdminClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        ManagedPolicyAttachmentProxy managedPolicyAttachmentProxy = new ManagedPolicyAttachmentProxy(proxy, proxyClient);
        InlinePolicyProxy inlinePolicyProxy = new InlinePolicyProxy(proxy, proxyClient);

        ResourceModel model = request.getDesiredResourceState();

        tags = model.getTags();

        return ProgressEvent.progress(model, callbackContext)
                .then(progress -> proxy.initiate("AWS-SSO-PermissionSet::Read", proxyClient, model, callbackContext)
                    .translateToServiceRequest(Translator::translateToReadRequest)
                    .makeServiceCall((readRequest, client) -> {
                        DescribePermissionSetResponse response = proxy.injectCredentialsAndInvokeV2(readRequest, client.client()::describePermissionSet);
                        if (tags == null || tags.isEmpty()) {
                            tags = Translator.ConvertToModelTag(getResourceTags(readRequest.instanceArn(),
                                    model.getPermissionSetArn(),
                                    proxy,
                                    proxyClient));
                        }

                        logger.log(String.format("%s has successfully been read.", ResourceModel.TYPE_NAME));
                        return response;
                    })
                    .handleError((describePermissionSetRequest, exception, client, resourceModel, context) -> {
                        if (exception instanceof ResourceNotFoundException) {
                            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
                        } else if (exception instanceof ThrottlingException && context.getRetryAttempts() >= RETRY_ATTEMPTS_ZERO) {
                            context.decrementRetryAttempts();
                            return ProgressEvent.defaultInProgressHandler(context, 5, model);
                        }
                        throw exception;

                    })
                    .done((readRequest, readResponse, proxyInvocation, resourceModel, context) -> {
                        ResourceModel outputModel = Translator.translateFromReadResponse(readResponse, resourceModel.getInstanceArn(), tags);

                        context.resetRetryAttempts(RETRY_ATTEMPTS);
                        return ProgressEvent.defaultInProgressHandler(context, 0, outputModel);
                    })
                )
                .then(progress -> {
                    ResourceModel outputModel = progress.getResourceModel();
                    CallbackContext context = progress.getCallbackContext();

                    try {
                        outputModel.setManagedPolicies(managedPolicyAttachmentProxy.getAttachedManagedPolicies(outputModel.getInstanceArn(),
                                outputModel.getPermissionSetArn()));
                        outputModel.setInlinePolicy(inlinePolicyProxy.getInlinePolicyForPermissionSet(outputModel.getInstanceArn(),
                                outputModel.getPermissionSetArn()));
                    } catch (ThrottlingException e) {
                        if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                            throw e;
                        }
                        context.decrementRetryAttempts();

                        return ProgressEvent.defaultInProgressHandler(progress.getCallbackContext(), 5, outputModel);
                    }
                    return ProgressEvent.defaultSuccessHandler(outputModel);
                });
    }
}
