package io.anserini.ann.queryparser;

import io.anserini.analysis.AnalyzerUtils;
import io.anserini.ann.fw.FakeWordsEncoderAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.SyntaxError;

import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;

public class CommonTermsQParserPlugin extends QParserPlugin {

    private static final String MAX_TERM_FREQUENCY_CONFIG_PARAM_NAME = "cutoff";
    private static final String MSM_CONFIG_PARAM_NAME = "msm";
    private static final String QUANTIZIER_CONFIG_PARAM_NAME = "q";
    private static final float DEFAULT_MAX_TERM_FREQUENCY = 0.01F;
    private static final int DEFAULT_QUANTIZATION_FACTOR = 60;
    private Analyzer vectorAnalyzer;

//    private float maxTermFrequency = DEFAULT_MAX_TERM_FREQUENCY;
    private float cutoff = DEFAULT_MAX_TERM_FREQUENCY;
    private int q = DEFAULT_QUANTIZATION_FACTOR;
    private float msm = 0f;

    @Override
    public void init(NamedList args) {
        SolrParams params = SolrParams.toSolrParams(args);

        // handle configuration parameters passed through solrconfig.xml
        final Integer q = params.getInt(QUANTIZIER_CONFIG_PARAM_NAME);
        if (q != null) {
            this.q = q;
        }
        final Float maxTermFrequency = params.getFloat(MAX_TERM_FREQUENCY_CONFIG_PARAM_NAME);
        if (maxTermFrequency != null) {
            this.cutoff = maxTermFrequency;
        }
        final Float msm = params.getFloat(MSM_CONFIG_PARAM_NAME);
        if (msm != null) {
            this.msm = msm;
        }
        this.vectorAnalyzer = new FakeWordsEncoderAnalyzer(this.q);
    }

    @Override
    public QParser createParser(final String queryString, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
        return new QParser(queryString, localParams, params, req) {

            @Override
            public Query parse() throws SyntaxError {
                String queryField = localParams.get("qf");
                String queryValue = localParams.get("v");
                CommonTermsQuery commonTermsQuery = new CommonTermsQuery(SHOULD, SHOULD, cutoff);
                for (String token : AnalyzerUtils.analyze(vectorAnalyzer, queryValue)) {
                    commonTermsQuery.add(new Term(queryField, token));
                }
                if (msm > 0) {
                    commonTermsQuery.setHighFreqMinimumNumberShouldMatch(msm);
                    commonTermsQuery.setLowFreqMinimumNumberShouldMatch(msm);
                }
                return commonTermsQuery;
            }
        };
    }
}