package io.anserini.ann.queryparser;

import io.anserini.analysis.AnalyzerUtils;
import io.anserini.ann.fw.FakeWordsEncoderAnalyzer;
import io.anserini.ann.lexlsh.LexicalLshAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;

import java.util.Optional;

import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;

public class AnnQParserPlugin extends QParserPlugin {

    private static final String DEPTH_CONFIG_PARAM_NAME = "depth";
    private static final String MAX_TERM_FREQUENCY_CONFIG_PARAM_NAME = "cutoff";
    private static final String MSM_CONFIG_PARAM_NAME = "msm";
    private static final String ANALYZER_CONFIG_PARAM_NAME = "analyzer";
    private static final String QUANTIZIER_CONFIG_PARAM_NAME = "q";
    private static final float DEFAULT_MAX_TERM_FREQUENCY = 0.01F;
    private static final int DEFAULT_QUANTIZATION_FACTOR = 60;
    private static final int DEFAULT_DEPTH = 10;
    private Analyzer vectorAnalyzer;

    //    private float maxTermFrequency = DEFAULT_MAX_TERM_FREQUENCY;
    private float cutoff = DEFAULT_MAX_TERM_FREQUENCY;
    private int q = DEFAULT_QUANTIZATION_FACTOR;
    private float msm = 0f;
    private int depth = DEFAULT_DEPTH;

    @Override
    public void init(NamedList args) {
        SolrParams params = SolrParams.toSolrParams(args);

        // handle configuration parameters passed through solrconfig.xml
        final Integer q = params.getInt(QUANTIZIER_CONFIG_PARAM_NAME);
        if (q != null) {
            this.q = q;
        }
        final Integer depth = params.getInt(DEPTH_CONFIG_PARAM_NAME);
        if (depth != null) {
            this.depth = depth;
        }
        final Float maxTermFrequency = params.getFloat(MAX_TERM_FREQUENCY_CONFIG_PARAM_NAME);
        if (maxTermFrequency != null) {
            this.cutoff = maxTermFrequency;
        }
        final Float msm = params.getFloat(MSM_CONFIG_PARAM_NAME);
        if (msm != null) {
            this.msm = msm;
        }
        final String analyzer = params.get(ANALYZER_CONFIG_PARAM_NAME, "fw");
        if (analyzer != null) {
            if (analyzer.equalsIgnoreCase("fw")) {
                this.vectorAnalyzer = new FakeWordsEncoderAnalyzer(this.q);
            } else if (analyzer.equalsIgnoreCase("lexlsh")) {
                this.vectorAnalyzer = new LexicalLshAnalyzer();
            } else {
                throw new IllegalArgumentException("missing " + ANALYZER_CONFIG_PARAM_NAME + " parameter");
            }
        }
    }

    @Override
    public QParser createParser(final String queryString, SolrParams localParams, SolrParams params, SolrQueryRequest req) {

        return new QParser(queryString, localParams, params, req) {

            @Override
            public Query parse() throws SolrException {

                final String queryField = Optional.ofNullable(localParams.get("qf"))
                                                  .orElseThrow(() -> new SolrException(SolrException.ErrorCode.BAD_REQUEST, "qf parameter is mandatory"));

                final String queryValue = Optional.ofNullable(localParams.get("v"))
                                                  .orElseThrow(() -> new SolrException(SolrException.ErrorCode.BAD_REQUEST, "v parameter is mandatory"));
                final CommonTermsQuery commonTermsQuery = new CommonTermsQuery(SHOULD, SHOULD, cutoff);
                for (String token : AnalyzerUtils.analyze(vectorAnalyzer, queryValue)) {
                    commonTermsQuery.add(new Term(queryField, token));
                }
                if (msm > 0) {
                    commonTermsQuery.setHighFreqMinimumNumberShouldMatch(msm);
                    commonTermsQuery.setLowFreqMinimumNumberShouldMatch(msm);
                }

//                TopScoreDocCollector results = TopScoreDocCollector.create(depth, Integer.MAX_VALUE);
//
//                try {
//                    req.getSearcher().search(commonTermsQuery, results);
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }

//                SolrParams computedLocalParams = new ModifiableSolrParams(localParams)
//                        .set(ReRankQParserPlugin.RERANK_QUERY, "{!vp f=" + field + " vector=\"" + vector + "\" lsh=\"false\"}")
//                        .setNonNull(ReRankQParserPlugin.RERANK_WEIGHT, reRankWeight)
//                        .set("q", lshQuery);
//                return ((AbstractReRankQuery) req.getCore().getQueryPlugin(ReRankQParserPlugin.NAME).
//                                                 .createParser(lshQuery, computedLocalParams, params, req)
//                                                 .getQuery()).wrap(commonTermsQuery);

                return commonTermsQuery;
            }
        };
    }
}