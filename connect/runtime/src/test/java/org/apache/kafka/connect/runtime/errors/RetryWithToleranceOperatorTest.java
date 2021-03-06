/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.connect.runtime.errors;

import org.apache.kafka.common.utils.MockTime;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.RetriableException;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.connect.runtime.errors.RetryWithToleranceOperator.RetryWithToleranceOperatorConfig;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ProcessingContext.class})
@PowerMockIgnore("javax.management.*")
public class RetryWithToleranceOperatorTest {

    @SuppressWarnings("unused")
    @Mock
    private Operation<String> mockOperation;

    @Mock
    ErrorHandlingMetrics errorHandlingMetrics;

    @Test
    public void testHandleExceptionInTransformations() {
        testHandleExceptionInStage(Stage.TRANSFORMATION, new Exception());
    }

    @Test
    public void testHandleExceptionInHeaderConverter() {
        testHandleExceptionInStage(Stage.HEADER_CONVERTER, new Exception());
    }

    @Test
    public void testHandleExceptionInValueConverter() {
        testHandleExceptionInStage(Stage.VALUE_CONVERTER, new Exception());
    }

    @Test
    public void testHandleExceptionInKeyConverter() {
        testHandleExceptionInStage(Stage.KEY_CONVERTER, new Exception());
    }

    @Test
    public void testHandleExceptionInTaskPut() {
        testHandleExceptionInStage(Stage.TASK_PUT, new org.apache.kafka.connect.errors.RetriableException("Test"));
    }

    @Test
    public void testHandleExceptionInTaskPoll() {
        testHandleExceptionInStage(Stage.TASK_POLL, new org.apache.kafka.connect.errors.RetriableException("Test"));
    }

    @Test(expected = ConnectException.class)
    public void testThrowExceptionInTaskPut() {
        testHandleExceptionInStage(Stage.TASK_PUT, new Exception());
    }

    @Test(expected = ConnectException.class)
    public void testThrowExceptionInTaskPoll() {
        testHandleExceptionInStage(Stage.TASK_POLL, new Exception());
    }

    @Test(expected = ConnectException.class)
    public void testThrowExceptionInKafkaConsume() {
        testHandleExceptionInStage(Stage.KAFKA_CONSUME, new Exception());
    }

    @Test(expected = ConnectException.class)
    public void testThrowExceptionInKafkaProduce() {
        testHandleExceptionInStage(Stage.KAFKA_PRODUCE, new Exception());
    }

    private void testHandleExceptionInStage(Stage type, Exception ex) {
        RetryWithToleranceOperator retryWithToleranceOperator = setupExecutor();
        retryWithToleranceOperator.execute(new ExceptionThrower(ex), type, ExceptionThrower.class);
        assertTrue(retryWithToleranceOperator.failed());
        PowerMock.verifyAll();
    }

    private RetryWithToleranceOperator setupExecutor() {
        RetryWithToleranceOperator retryWithToleranceOperator = new RetryWithToleranceOperator();
        Map<String, Object> props = config(RetryWithToleranceOperator.RETRY_TIMEOUT, "0");
        props.put(RetryWithToleranceOperator.TOLERANCE_LIMIT, "all");
        retryWithToleranceOperator.configure(props);
        retryWithToleranceOperator.metrics(errorHandlingMetrics);
        return retryWithToleranceOperator;
    }

    @Test
    public void testExecAndHandleRetriableErrorOnce() throws Exception {
        execAndHandleRetriableError(1, 300, new RetriableException("Test"));
    }

    @Test
    public void testExecAndHandleRetriableErrorThrice() throws Exception {
        execAndHandleRetriableError(3, 2100, new RetriableException("Test"));
    }

    @Test
    public void testExecAndHandleNonRetriableErrorOnce() throws Exception {
        execAndHandleNonRetriableError(1, 0, new Exception("Non Retriable Test"));
    }

    @Test
    public void testExecAndHandleNonRetriableErrorThrice() throws Exception {
        execAndHandleNonRetriableError(3, 0, new Exception("Non Retriable Test"));
    }

    public void execAndHandleRetriableError(int numRetriableExceptionsThrown, long expectedWait, Exception e) throws Exception {
        MockTime time = new MockTime(0, 0, 0);
        RetryWithToleranceOperator retryWithToleranceOperator = new RetryWithToleranceOperator(time);
        Map<String, Object> props = config(RetryWithToleranceOperator.RETRY_TIMEOUT, "6000");
        props.put(RetryWithToleranceOperator.TOLERANCE_LIMIT, "all");
        retryWithToleranceOperator.configure(props);
        retryWithToleranceOperator.metrics(errorHandlingMetrics);

        EasyMock.expect(mockOperation.call()).andThrow(e).times(numRetriableExceptionsThrown);
        EasyMock.expect(mockOperation.call()).andReturn("Success");

        replay(mockOperation);

        String result = retryWithToleranceOperator.execAndHandleError(mockOperation, Exception.class);
        assertFalse(retryWithToleranceOperator.failed());
        assertEquals("Success", result);
        assertEquals(expectedWait, time.hiResClockMs());

        PowerMock.verifyAll();
    }

    public void execAndHandleNonRetriableError(int numRetriableExceptionsThrown, long expectedWait, Exception e) throws Exception {
        MockTime time = new MockTime(0, 0, 0);
        RetryWithToleranceOperator retryWithToleranceOperator = new RetryWithToleranceOperator(time);
        Map<String, Object> props = config(RetryWithToleranceOperator.RETRY_TIMEOUT, "6000");
        props.put(RetryWithToleranceOperator.TOLERANCE_LIMIT, "all");
        retryWithToleranceOperator.configure(props);
        retryWithToleranceOperator.metrics(errorHandlingMetrics);

        EasyMock.expect(mockOperation.call()).andThrow(e).times(numRetriableExceptionsThrown);
        EasyMock.expect(mockOperation.call()).andReturn("Success");

        replay(mockOperation);

        String result = retryWithToleranceOperator.execAndHandleError(mockOperation, Exception.class);
        assertTrue(retryWithToleranceOperator.failed());
        assertNull(result);
        assertEquals(expectedWait, time.hiResClockMs());

        PowerMock.verifyAll();
    }

    @Test
    public void testCheckRetryLimit() {
        MockTime time = new MockTime(0, 0, 0);
        RetryWithToleranceOperator retryWithToleranceOperator = new RetryWithToleranceOperator(time);
        Map<String, Object> props = config(RetryWithToleranceOperator.RETRY_TIMEOUT, "500");
        props.put(RetryWithToleranceOperator.RETRY_DELAY_MAX_MS, "100");
        retryWithToleranceOperator.configure(props);

        time.setCurrentTimeMs(100);
        assertTrue(retryWithToleranceOperator.checkRetry(0));

        time.setCurrentTimeMs(200);
        assertTrue(retryWithToleranceOperator.checkRetry(0));

        time.setCurrentTimeMs(400);
        assertTrue(retryWithToleranceOperator.checkRetry(0));

        time.setCurrentTimeMs(499);
        assertTrue(retryWithToleranceOperator.checkRetry(0));

        time.setCurrentTimeMs(501);
        assertFalse(retryWithToleranceOperator.checkRetry(0));

        time.setCurrentTimeMs(600);
        assertFalse(retryWithToleranceOperator.checkRetry(0));
    }

    @Test
    public void testBackoffLimit() {
        MockTime time = new MockTime(0, 0, 0);
        RetryWithToleranceOperator retryWithToleranceOperator = new RetryWithToleranceOperator(time);

        Map<String, Object> props = config(RetryWithToleranceOperator.RETRY_TIMEOUT, "5");
        props.put(RetryWithToleranceOperator.RETRY_DELAY_MAX_MS, "5000");
        retryWithToleranceOperator.configure(props);

        long prevTs = time.hiResClockMs();
        retryWithToleranceOperator.backoff(1, 5000);
        assertEquals(300, time.hiResClockMs() - prevTs);

        prevTs = time.hiResClockMs();
        retryWithToleranceOperator.backoff(2, 5000);
        assertEquals(600, time.hiResClockMs() - prevTs);

        prevTs = time.hiResClockMs();
        retryWithToleranceOperator.backoff(3, 5000);
        assertEquals(1200, time.hiResClockMs() - prevTs);

        prevTs = time.hiResClockMs();
        retryWithToleranceOperator.backoff(4, 5000);
        assertEquals(2400, time.hiResClockMs() - prevTs);

        prevTs = time.hiResClockMs();
        retryWithToleranceOperator.backoff(5, 5000);
        assertEquals(500, time.hiResClockMs() - prevTs);

        prevTs = time.hiResClockMs();
        retryWithToleranceOperator.backoff(6, 5000);
        assertEquals(0, time.hiResClockMs() - prevTs);

        PowerMock.verifyAll();
    }

    @Test
    public void testToleranceLimit() {
        RetryWithToleranceOperator retryWithToleranceOperator = new RetryWithToleranceOperator();
        retryWithToleranceOperator.configure(config(RetryWithToleranceOperator.TOLERANCE_LIMIT, "none"));
        retryWithToleranceOperator.metrics(errorHandlingMetrics);
        retryWithToleranceOperator.markAsFailed();
        assertFalse("should not tolerate any errors", retryWithToleranceOperator.withinToleranceLimits());

        retryWithToleranceOperator = new RetryWithToleranceOperator();
        retryWithToleranceOperator.configure(config(RetryWithToleranceOperator.TOLERANCE_LIMIT, "all"));
        retryWithToleranceOperator.metrics(errorHandlingMetrics);
        retryWithToleranceOperator.markAsFailed();
        retryWithToleranceOperator.markAsFailed();
        assertTrue("should tolerate all errors", retryWithToleranceOperator.withinToleranceLimits());

        retryWithToleranceOperator = new RetryWithToleranceOperator();
        retryWithToleranceOperator.configure(config(RetryWithToleranceOperator.TOLERANCE_LIMIT, "none"));
        assertTrue("no tolerance is within limits if no failures", retryWithToleranceOperator.withinToleranceLimits());
    }

    @Test
    public void testDefaultConfigs() {
        RetryWithToleranceOperatorConfig configuration;
        configuration = new RetryWithToleranceOperatorConfig(new HashMap<>());
        assertEquals(configuration.retryTimeout(), 0);
        assertEquals(configuration.retryDelayMax(), 60000);
        assertEquals(configuration.toleranceLimit(), ToleranceType.NONE);

        PowerMock.verifyAll();
    }

    @Test
    public void testConfigs() {
        RetryWithToleranceOperatorConfig configuration;
        configuration = new RetryWithToleranceOperatorConfig(config("retry.timeout", "100"));
        assertEquals(configuration.retryTimeout(), 100);

        configuration = new RetryWithToleranceOperatorConfig(config("retry.delay.max.ms", "100"));
        assertEquals(configuration.retryDelayMax(), 100);

        configuration = new RetryWithToleranceOperatorConfig(config("allowed.max", "none"));
        assertEquals(configuration.toleranceLimit(), ToleranceType.NONE);

        PowerMock.verifyAll();
    }

    Map<String, Object> config(String key, Object val) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(key, val);
        return configs;
    }

    private static class ExceptionThrower implements Operation<Object> {
        private Exception e;

        public ExceptionThrower(Exception e) {
            this.e = e;
        }

        @Override
        public Object call() throws Exception {
            throw e;
        }
    }
}
