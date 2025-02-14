package com.royvanrijn.optimalopen;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.TestStringMapGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Map;

public class MapTest {

    public static Test suite() {
        TestSuite suite = new TestSuite("All tests");
        suite.addTest(tests("Simple", new TestStringMapGenerator() {
            @Override
            protected Map<String, String> create(Map.Entry<String, String>[] entries) {
                return populate(new OptimalOpenHashMap<>(), entries);
            }
        }));
        return suite;
    }

    private static TestSuite tests(final String name, TestStringMapGenerator generator) {
        return MapTestSuiteBuilder
                .using(generator)
                .named(name)
                .withFeatures(
                        MapFeature.GENERAL_PURPOSE,
                        MapFeature.ALLOWS_NULL_VALUES,
                        MapFeature.ALLOWS_ANY_NULL_QUERIES,
                        MapFeature.RESTRICTS_KEYS,
                        MapFeature.RESTRICTS_VALUES,
                        CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
                        CollectionFeature.SERIALIZABLE,
                        CollectionSize.ANY)
                .createTestSuite();
    }

    private static <T, M extends Map<T, String>> M populate(M map, Map.Entry<T, String>[] entries) {
        for (Map.Entry<T, String> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
