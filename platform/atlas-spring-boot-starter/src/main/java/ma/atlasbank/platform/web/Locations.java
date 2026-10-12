package ma.atlasbank.platform.web;

import java.net.URI;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds {@code Location} headers as relative references (RFC 9110 section 10.2.2): they stay valid
 * behind any chain of proxies without trusting client-supplied {@code X-Forwarded-*} headers.
 */
public final class Locations {

    private Locations() {
    }

    /** {@code <current request path>/<id>}, e.g. {@code /api/v1/customers/42} after a POST on the collection. */
    public static URI childOfCurrentRequest(Object id) {
        String collection = ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
        return UriComponentsBuilder.fromPath(collection).pathSegment(String.valueOf(id)).build().toUri();
    }

    public static URI of(String pathTemplate, Object... variables) {
        return UriComponentsBuilder.fromPath(pathTemplate).buildAndExpand(variables).toUri();
    }
}
