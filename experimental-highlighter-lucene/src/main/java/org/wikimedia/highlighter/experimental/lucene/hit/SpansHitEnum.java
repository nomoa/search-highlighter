package org.wikimedia.highlighter.experimental.lucene.hit;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanScorer;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.wikimedia.highlighter.experimental.lucene.WrappedExceptionFromLucene;
import org.wikimedia.highlighter.experimental.lucene.hit.weight.BasicQueryWeigher.TermInfos;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.hit.EmptyHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.ReplayingHitEnum;
import org.wikimedia.search.highlighter.experimental.tools.GraphvizHitEnumGenerator;

public class SpansHitEnum implements HitEnum, SpanCollector {
    private final Spans spans;
    private final HitEnum wrapped;
    private final ReplayingHitEnum toReplay;
    private final TermInfos termInfos;
    
    public SpansHitEnum(Spans spans, HitEnum wrapped, TermInfos termInfos) {
        this.spans = spans;
        this.wrapped = wrapped;
        this.toReplay = new ReplayingHitEnum();
        this.termInfos = termInfos;
    }
    
    public static HitEnum wrap(HitEnum wrapped, IndexSearcher searcher, LeafReaderContext context, SpanQuery query, int docId, TermInfos termInfos) throws IOException {
        SpanWeight weight = query.createWeight(searcher, false);
        SpanScorer scorer = weight.scorer(context);
        if( scorer.getSpans().advance(docId) == DocIdSetIterator.NO_MORE_DOCS ) {
            return EmptyHitEnum.INSTANCE;
        };
        return new SpansHitEnum(scorer.getSpans(), wrapped, termInfos);
    }

    @Override
    public int startOffset() {
        return toReplay.startOffset();
    }

    @Override
    public int endOffset() {
        return toReplay.endOffset();
    }

    @Override
    public boolean next() {
        while(wrapped.next()) {
            try {
                spans.collect(this);
            } catch (IOException e) {
                throw new WrappedExceptionFromLucene(e);
            }
        }
        return toReplay.next();
    }

    @Override
    public int position() {
        return toReplay.position();
    }

    @Override
    public float queryWeight() {
        return toReplay.queryWeight();
    }

    @Override
    public float corpusWeight() {
        return toReplay.corpusWeight();
    }

    @Override
    public int source() {
        return toReplay.source();
    }

    @Override
    public void toGraph(GraphvizHitEnumGenerator generator) {
        
    }

    @Override
    public void collectLeaf(PostingsEnum postings, int position, Term term) throws IOException {
        if(wrapped.position() == position && wrapped.source() == termInfos.get(term.bytes()).source) {
            toReplay.recordCurrent(wrapped);
        }
    }

    @Override
    public void reset() {}
}
