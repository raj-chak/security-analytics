/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.securityanalytics.util;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.securityanalytics.model.Detector;
import org.opensearch.securityanalytics.model.DetectorInput;

public class DetectorUtils {

    public static final String DETECTOR_TYPE_PATH = "detector.detector_type";
    public static final String DETECTOR_ID_FIELD = "detector_id";

    public static List<Detector> getDetectors(SearchResponse response, NamedXContentRegistry xContentRegistry) throws IOException {
        List<Detector> detectors = new LinkedList<>();
        for (SearchHit hit : response.getHits()) {
            XContentParser xcp = XContentType.JSON.xContent().createParser(
                    xContentRegistry,
                    LoggingDeprecationHandler.INSTANCE, hit.getSourceAsString());
            Detector detector = Detector.docParse(xcp, hit.getId(), hit.getVersion());
            detectors.add(detector);
        }
        return detectors;
    }

    public static void getAllDetectorInputs(Client client, NamedXContentRegistry xContentRegistry, ActionListener<Set<String>> actionListener) {

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(FetchSourceContext.FETCH_SOURCE);
        searchSourceBuilder.seqNoAndPrimaryTerm(true);
        searchSourceBuilder.version(true);

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.source(searchSourceBuilder);
        searchRequest.indices(Detector.DETECTORS_INDEX);

        client.search(searchRequest, new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse response) {
                Set<String> allDetectorIndices = new HashSet<>();
                try {
                    List<Detector> detectors = DetectorUtils.getDetectors(response, xContentRegistry);
                    for (Detector detector : detectors) {
                        for (DetectorInput input : detector.getInputs()) {
                            allDetectorIndices.addAll(input.getIndices());
                        }
                    }
                } catch (IOException e) {
                    actionListener.onFailure(e);
                }
                actionListener.onResponse(allDetectorIndices);
            }

            @Override
            public void onFailure(Exception e) {
                actionListener.onFailure(e);
            }
        });
    }
}