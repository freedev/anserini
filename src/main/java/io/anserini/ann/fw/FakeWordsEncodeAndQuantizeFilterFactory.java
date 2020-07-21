package io.anserini.ann.fw;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.io.IOException;
import java.util.Map;

public class FakeWordsEncodeAndQuantizeFilterFactory extends TokenFilterFactory implements ResourceLoaderAware {

    private static final int DEFAULT_Q = FakeWordsEncoderAnalyzer.DEFAULT_Q;
    private final float q;

    public FakeWordsEncodeAndQuantizeFilterFactory(Map<String, String> args) {
        super(args);
        this.q = getFloat(args, "q", DEFAULT_Q);
    }

    @Override
    public void inform(ResourceLoader resourceLoader) throws IOException {
        // do nothing
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new FakeWordsEncodeAndQuantizeFilter(tokenStream, q);
    }
}
