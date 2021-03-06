package com.github.kristofa.brave.http;

import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceKeys;
import com.github.kristofa.brave.internal.Nullable;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

public class HttpClientRequestAdapter implements ClientRequestAdapter {

    private final HttpClientRequest request;
    private final SpanNameProvider spanNameProvider;

    public HttpClientRequestAdapter(HttpClientRequest request, SpanNameProvider spanNameProvider) {
        this.request = request;
        this.spanNameProvider = spanNameProvider;
    }


    @Override
    public String getSpanName() {
        return spanNameProvider.spanName(request);
    }

    @Override
    public void addSpanIdToRequest(@Nullable SpanId spanId) {
        if (spanId == null) {
            request.addHeader(BraveHttpHeaders.Sampled.getName(), "0");
        } else {
            request.addHeader(BraveHttpHeaders.Sampled.getName(), "1");
            request.addHeader(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(spanId.traceId));
            request.addHeader(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId.spanId));
            if (spanId.nullableParentId() != null) {
                request.addHeader(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(spanId.parentId));
            }
        }
    }


    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        URI uri = request.getUri();
        KeyValueAnnotation annotation = KeyValueAnnotation.create(TraceKeys.HTTP_URL, uri.toString());
        return Arrays.asList(annotation);
    }
}
