package ma.atlasbank.platform.security;

import ma.atlasbank.commons.security.Requester;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers declare a {@link Requester} parameter instead of reading the security context.
 */
public class RequesterArgumentResolver implements HandlerMethodArgumentResolver {

    private final RequesterResolver requesterResolver;

    public RequesterArgumentResolver(RequesterResolver requesterResolver) {
        this.requesterResolver = requesterResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Requester.class.equals(parameter.getParameterType());
    }

    @Override
    public Requester resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        return requesterResolver.resolve(SecurityContextHolder.getContext().getAuthentication());
    }
}
