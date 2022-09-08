/*
 *   Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package software.amazon.cloudwatchlogs.emf.logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import software.amazon.cloudwatchlogs.emf.environment.Environment;
import software.amazon.cloudwatchlogs.emf.environment.EnvironmentProvider;
import software.amazon.cloudwatchlogs.emf.exception.DimensionSetExceededException;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.MetricsContext;
import software.amazon.cloudwatchlogs.emf.sinks.SinkShunt;

public class MetricsLoggerTest {
    private MetricsLogger logger;
    private EnvironmentProvider envProvider;
    private SinkShunt sink;
    private Environment environment;

    @Before
    public void setUp() {
        envProvider = mock(EnvironmentProvider.class);
        environment = mock(Environment.class);
        sink = new SinkShunt();

        when(envProvider.resolveEnvironment())
                .thenReturn(CompletableFuture.completedFuture(environment));
        when(environment.getSink()).thenReturn(sink);
        logger = new MetricsLogger(envProvider);
    }

    @Test
    public void testPutProperty() {
        String propertyName = "Property";
        String propertyValue = "PropValue";
        logger.putProperty(propertyName, propertyValue);
        logger.flush();

        Assert.assertEquals(propertyValue, sink.getContext().getProperty(propertyName));
    }

    @Test
    public void testPutDimension() throws DimensionSetExceededException {
        String dimensionName = "dim";
        String dimensionValue = "dimValue";
        logger.putDimensions(DimensionSet.of(dimensionName, dimensionValue));
        logger.flush();

        Assert.assertEquals(1, sink.getContext().getDimensions().size());
        Assert.assertEquals(
                dimensionValue,
                sink.getContext().getDimensions().get(0).getDimensionValue(dimensionName));
    }

    @Test
    public void testOverrideDefaultDimensions() throws DimensionSetExceededException {
        String dimensionName = "dim";
        String dimensionValue = "dimValue";
        String defaultDimName = "defaultDim";
        String defaultDimValue = "defaultDimValue";

        MetricsContext metricsContext = new MetricsContext();
        metricsContext.setDefaultDimensions(DimensionSet.of(defaultDimName, defaultDimValue));
        metricsContext.setDimensions(DimensionSet.of(dimensionName, dimensionValue));
        logger = new MetricsLogger(envProvider, metricsContext);
        logger.setDimensions(DimensionSet.of(dimensionName, dimensionValue));
        logger.flush();

        Assert.assertEquals(1, sink.getContext().getDimensions().size());
        Assert.assertNull(
                sink.getContext().getDimensions().get(0).getDimensionValue(defaultDimName));
    }

    @Test
    public void testResetWithDefaultDimensions() throws DimensionSetExceededException {
        String dimensionName = "dim";
        String dimensionValue = "dimValue";
        logger.putDimensions(DimensionSet.of("foo", "bar"));
        logger.resetDimensions(true);
        logger.putDimensions(DimensionSet.of(dimensionName, dimensionValue));
        logger.flush();

        Assert.assertEquals(sink.getContext().getDimensions().size(), 1);
        Assert.assertEquals(sink.getContext().getDimensions().get(0).getDimensionKeys().size(), 4);
        Assert.assertEquals(
                sink.getContext().getDimensions().get(0).getDimensionValue(dimensionName),
                dimensionValue);
    }

    @Test
    public void testResetWithoutDefaultDimensions() throws DimensionSetExceededException {
        String dimensionName = "dim";
        String dimensionValue = "dimValue";
        logger.putDimensions(DimensionSet.of("foo", "bar"));
        logger.resetDimensions(false);
        logger.putDimensions(DimensionSet.of(dimensionName, dimensionValue));
        logger.flush();

        Assert.assertEquals(sink.getContext().getDimensions().size(), 1);
        Assert.assertEquals(sink.getContext().getDimensions().get(0).getDimensionKeys().size(), 1);
        Assert.assertEquals(
                sink.getContext().getDimensions().get(0).getDimensionValue(dimensionName),
                dimensionValue);
    }

    @Test
    public void testOverridePreviousDimensions() throws DimensionSetExceededException {

        String dimensionName = "dim";
        String dimensionValue = "dimValue";
        logger.putDimensions(DimensionSet.of("foo", "bar"));
        logger.setDimensions(DimensionSet.of(dimensionName, dimensionValue));
        logger.flush();

        Assert.assertEquals(1, sink.getContext().getDimensions().size());
        Assert.assertEquals(1, sink.getContext().getDimensions().get(0).getDimensionKeys().size());
        Assert.assertEquals(
                dimensionValue,
                sink.getContext().getDimensions().get(0).getDimensionValue(dimensionName));
    }

    @Test
    public void testSetDimensionsAndPreserveDefault() throws DimensionSetExceededException {
        String dimensionName = "dim";
        String dimensionValue = "dimValue";
        logger.putDimensions(DimensionSet.of("foo", "bar"));
        logger.setDimensions(true, DimensionSet.of(dimensionName, dimensionValue));
        logger.flush();

        Assert.assertEquals(sink.getContext().getDimensions().size(), 1);
        Assert.assertEquals(sink.getContext().getDimensions().get(0).getDimensionKeys().size(), 4);
        Assert.assertEquals(
                sink.getContext().getDimensions().get(0).getDimensionValue(dimensionName),
                dimensionValue);
    }

    @Test
    public void testSetNamespace() {

        String namespace = "testNamespace";
        logger.setNamespace(namespace);
        logger.flush();

        Assert.assertEquals(namespace, sink.getContext().getNamespace());
    }

    @Test
    public void testFlushWithDefaultTimestamp() {
        logger.flush();
        Assert.assertNotNull(sink.getContext().getTimestamp());
    }

    @Test
    public void testSetTimestamp() {
        Instant now = Instant.now();
        logger.setTimestamp(now);
        logger.flush();

        Assert.assertEquals(now, sink.getContext().getTimestamp());
    }

    @Test
    public void testFlushWithConfiguredServiceName() throws DimensionSetExceededException {
        String serviceName = "TestServiceName";
        when(environment.getName()).thenReturn(serviceName);
        logger.flush();

        expectDimension("ServiceName", serviceName);
    }

    @Test
    public void testFlushWithConfiguredServiceType() throws DimensionSetExceededException {
        String serviceType = "TestServiceType";
        when(environment.getType()).thenReturn(serviceType);
        logger.flush();

        expectDimension("ServiceType", serviceType);
    }

    @Test
    public void testFlushWithConfiguredLogGroup() throws DimensionSetExceededException {
        String logGroup = "MyLogGroup";
        when(environment.getLogGroupName()).thenReturn(logGroup);
        logger.flush();

        expectDimension("LogGroup", logGroup);
    }

    @Test
    public void testFlushWithDefaultDimensionDefined() throws DimensionSetExceededException {
        MetricsContext metricsContext = new MetricsContext();
        metricsContext.setDefaultDimensions(DimensionSet.of("foo", "bar"));
        logger = new MetricsLogger(envProvider, metricsContext);
        String logGroup = "MyLogGroup";
        when(environment.getLogGroupName()).thenReturn(logGroup);
        logger.flush();

        expectDimension("foo", "bar");
        expectDimension("LogGroup", null);
    }

    @SuppressWarnings("")
    @Test
    public void testUseDefaultEnvironmentOnResolverException()
            throws DimensionSetExceededException {
        String serviceType = "TestServiceType";
        CompletableFuture<Environment> future =
                CompletableFuture.supplyAsync(
                        () -> {
                            throw new RuntimeException("UnExpected");
                        });
        EnvironmentProvider envProvider = mock(EnvironmentProvider.class);
        when(envProvider.resolveEnvironment()).thenReturn(future);
        when(envProvider.getDefaultEnvironment()).thenReturn(environment);
        when(environment.getType()).thenReturn(serviceType);
        MetricsLogger logger = new MetricsLogger(envProvider);
        logger.flush();

        verify(envProvider).getDefaultEnvironment();
        expectDimension("ServiceType", serviceType);
    }

    @Test
    public void testNoDefaultDimensions() throws DimensionSetExceededException {
        MetricsLogger logger = new MetricsLogger(envProvider);
        logger.setDimensions();
        logger.putMetric("Count", 1);
        logger.flush();
        List<DimensionSet> dimensions = sink.getContext().getDimensions();

        assertEquals(0, dimensions.size());
        assertEquals(1, sink.getLogEvents().size());

        String logEvent = sink.getLogEvents().get(0);
        assertTrue(logEvent.contains("\"Dimensions\":[]"));
    }

    @Test
    public void testNoDefaultDimensionsAfterSetDimension() throws DimensionSetExceededException {
        MetricsLogger logger = new MetricsLogger(envProvider);

        logger.setDimensions(DimensionSet.of("Name", "Test"));
        logger.flush();
        expectDimension("Name", "Test");
    }

    @Test
    public void testFlushPreserveDimensions() throws DimensionSetExceededException {
        MetricsLogger logger = new MetricsLogger(envProvider);
        logger.setDimensions(DimensionSet.of("Name", "Test"));
        logger.flush();
        expectDimension("Name", "Test");

        logger.flush();
        expectDimension("Name", "Test");
    }

    @Test
    public void testFlushDoesntPreserveDimensions() throws DimensionSetExceededException {
        logger.putDimensions(DimensionSet.of("Name", "Test"));
        logger.setFlushPreserveDimensions(false);

        logger.flush();
        Assert.assertEquals(sink.getContext().getDimensions().get(0).getDimensionKeys().size(), 4);
        expectDimension("Name", "Test");

        logger.flush();
        Assert.assertEquals(sink.getContext().getDimensions().get(0).getDimensionKeys().size(), 3);
        expectDimension("Name", null);
    }

    @Test
    public void testFlushDoesntPreserveMetrics() {
        MetricsLogger logger = new MetricsLogger(envProvider);
        logger.setDimensions(DimensionSet.of("Name", "Test"));
        logger.putMetric("Count", 1.0);
        logger.flush();
        assertTrue(sink.getLogEvents().get(0).contains("Count"));

        logger.flush();
        assertFalse(sink.getLogEvents().get(0).contains("Count"));
    }

    @Test
    public void testNoDimensionsAfterSetEmptyDimensionSet() throws DimensionSetExceededException {
        MetricsLogger logger = new MetricsLogger(envProvider);

        logger.setDimensions();
        logger.flush();

        List<DimensionSet> dimensions = sink.getContext().getDimensions();
        assertEquals(0, dimensions.size());
    }

    @Test
    public void testNoDimensionsAfterSetEmptyDimensionSetWithMultipleFlush()
            throws DimensionSetExceededException {
        MetricsLogger logger = new MetricsLogger(envProvider);

        logger.setDimensions();
        logger.flush();

        assertEquals(0, sink.getContext().getDimensions().size());

        logger.flush();
        assertEquals(0, sink.getContext().getDimensions().size());
    }

    private void expectDimension(String dimension, String value)
            throws DimensionSetExceededException {
        List<DimensionSet> dimensions = sink.getContext().getDimensions();
        assertEquals(1, dimensions.size());
        assertEquals(value, dimensions.get(0).getDimensionValue(dimension));
    }
}
