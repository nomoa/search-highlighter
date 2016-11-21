package org.wikimedia.highlighter.experimental.elasticsearch;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.elasticsearch.action.search.SearchRequestBuilder;

import com.google.common.collect.ImmutableList;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
//import org.elasticsearch.plugin.analysis.icu.AnalysisICUPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.wikimedia.highlighter.experimental.elasticsearch.plugin.ExperimentalHighlighterPlugin;

@ClusterScope(scope = ESIntegTestCase.Scope.SUITE, transportClientRatio = 0.0)
public abstract class AbstractExperimentalHighlighterIntegrationTestBase extends
ESIntegTestCase {
    protected static final List<String> HIT_SOURCES = ImmutableList.of("postings", "vectors",
            "analyze");

    protected HighlightBuilder newHLBuilder() {
        return new HighlightBuilder()
                .highlighterType("experimental")
                .field("test");
    }

    protected SearchRequestBuilder testSearch() {
        return testSearch(termQuery("test", "test"), null);
    }

    /**
     * A simple search for the term "test".
     */
    protected SearchRequestBuilder testSearch(Consumer<HighlightBuilder> func) {
        return testSearch(termQuery("test", "test"), func);
    }

    protected SearchRequestBuilder testSearch(QueryBuilder builder) {
        return testSearch(builder, x->{});
    }
    
    protected Consumer<HighlightBuilder> field(HighlightBuilder.Field field) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                hb.field(field);
            }
        };
    }
    
    protected Consumer<HighlightBuilder> field(String field) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                hb.field(field);
            }
        };
    }
    
    protected Consumer<HighlightBuilder> order(String order) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                hb.order(order);
            }
        };
    }

    protected Consumer<HighlightBuilder> options(Map<String, Object> options) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                if(hb.options() == null) {
                    hb.options(new HashMap<>());
                }
                hb.options().putAll(options);
            }
        };
    }
    
    protected Consumer<HighlightBuilder> fragmentSize(Integer size) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                hb.fragmentSize(size);
            }
        };
    }

    protected Consumer<HighlightBuilder> option(String name, Object value) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                if(hb.options() == null) {
                    hb.options(new HashMap<>());
                }
                hb.options().put(name, value);
            }
        };
    }

    protected Consumer<HighlightBuilder> hitSource(String hitSource) {
        return option("hit_source", hitSource);
    }
    /**
     * A simple search for the term test.
     */
    protected SearchRequestBuilder testSearch(QueryBuilder builder, Consumer<HighlightBuilder> func) {
        HighlightBuilder hbuilder = new HighlightBuilder()
                .highlighterType("experimental")
                .field("test");
        if(func != null) {
            func.accept(hbuilder);
        }
        
        return client().prepareSearch("test").setTypes("test").setQuery(builder)
                .highlighter(hbuilder)
                .setSize(1);
    }

    protected void buildIndex() throws IOException {
        buildIndex(true, true, between(1, 5));
    }

    protected void buildIndex(boolean offsetsInPostings, boolean fvhLikeTermVectors, int shards)
            throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("test").startObject("properties");
        mapping.startObject("bar").field("type", "integer").endObject();
        addField(mapping, "test", offsetsInPostings, fvhLikeTermVectors);
        addField(mapping, "test2", offsetsInPostings, fvhLikeTermVectors);
        mapping.startObject("foo").field("type").value("object").startObject("properties");
        addField(mapping, "test", offsetsInPostings, fvhLikeTermVectors);
        mapping.endObject().endObject().endObject().endObject().endObject();

        XContentBuilder settings = jsonBuilder().startObject().startObject("index");
        settings.field("number_of_shards", shards);
        settings.startObject("analysis");
        settings.startObject("analyzer");
        {
            settings.startObject("chars").field("tokenizer", "chars").endObject();
            /*
             * This is a clone of the English analyzer cirrus uses that we can
             * use to run down errors that come up in CirrusSearch.
             */
            settings.startObject("cirrus_english");
            {
                settings.field("tokenizer", "standard");
                settings.array("filter", //
                        "standard", //
                        "aggressive_splitting", //
                        "possessive_english", //
                        //"icu_normalizer", //
                        "stop", //
                        "kstem", //
                        "custom_stem", //
                        "asciifolding_preserve" //
                        );
                settings.array("char_filter", "word_break_helper");
            }
            settings.endObject();
        }
        settings.endObject();
        settings.startObject("tokenizer");
        {
            settings.startObject("chars");
            {
                settings.field("type", "pattern");
                settings.field("pattern", "(.)");
                settings.field("group", 0);
            }
            settings.endObject();
        }
        settings.endObject();
        settings.startObject("filter");
        {
            settings.startObject("possessive_english");
            {
                settings.field("type", "stemmer");
                settings.field("language", "possessive_english");
            }
            settings.endObject();
            settings.startObject("aggressive_splitting");
            {
                settings.field("type", "word_delimiter");
                settings.field("stem_possessive_english", "false");
                settings.field("preserve_original", "false");
            }
            settings.endObject();
            settings.startObject("custom_stem");
            {
                settings.field("type", "stemmer_override");
                settings.field("rules", "guidelines => guideline");
            }
            settings.endObject();
            settings.startObject("asciifolding_preserve");
            {
                settings.field("type", "asciifolding");
                settings.field("preserve_original", "true");
            }
            settings.endObject();
            /*
            settings.startObject("icu_normalizer");
            {
                settings.field("type", "icu_normalizer");
                settings.field("name", "nfkc_cf");
            }
            settings.endObject();
            */
        }
        settings.endObject();
        settings.startObject("char_filter");
        {
            settings.startObject("word_break_helper");
            {
                settings.field("type", "mapping");
                settings.array("mappings", //
                        "_=>\\u0020", //
                        ".=>\\u0020", //
                        "(=>\\u0020", //
                        ")=>\\u0020");
            }
            settings.endObject();
        }
        settings.endObject();
        settings.endObject();
        settings.endObject();
        settings.endObject();
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("test", mapping));
        ensureYellow();
    }

    private void addField(XContentBuilder builder, String name, boolean offsetsInPostings,
            boolean fvhLikeTermVectors) throws IOException {
        builder.startObject(name).field("type", "text");
        addProperties(builder, offsetsInPostings, fvhLikeTermVectors);
        builder.startObject("fields");
        addSubField(builder, "whitespace", "whitespace", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "english", "english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "english2", "english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "cirrus_english", "cirrus_english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "chars", "chars", offsetsInPostings, fvhLikeTermVectors);
        builder.endObject().endObject();
    }

    private void addSubField(XContentBuilder builder, String name, String analyzer,
            boolean offsetsInPostings, boolean fvhLikeTermVectors) throws IOException {
        builder.startObject(name);
        builder.field("type", "text");
        builder.field("analyzer", analyzer);
        addProperties(builder, offsetsInPostings, fvhLikeTermVectors);
        builder.endObject();
    }

    private void addProperties(XContentBuilder builder, boolean offsetsInPostings,
            boolean fvhLikeTermVectors) throws IOException {
        if (offsetsInPostings) {
            builder.field("index_options", "offsets");
        }
        if (fvhLikeTermVectors) {
            builder.field("term_vector", "with_positions_offsets");
        }
    }

    protected void indexTestData() {
        indexTestData("tests very simple test");
    }

    protected void indexTestData(Object contents) {
        client().prepareIndex("test", "test", "1").setSource("test", contents).get();
        refresh();
    }

    /**
     * Enable plugin loading.
     */
    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.unmodifiableCollection(
                Arrays.asList(ExperimentalHighlighterPlugin.class
                        //, AnalysisICUPlugin.class
                ));
    }
}
