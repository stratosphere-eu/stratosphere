/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.pact.runtime.task;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import eu.stratosphere.pact.common.generic.GenericCrosser;
import eu.stratosphere.pact.common.stubs.Collector;
import eu.stratosphere.pact.common.stubs.CrossStub;
import eu.stratosphere.pact.common.type.PactRecord;
import eu.stratosphere.pact.runtime.task.util.TaskConfig.LocalStrategy;
import eu.stratosphere.pact.runtime.test.util.DelayingInfinitiveInputIterator;
import eu.stratosphere.pact.runtime.test.util.DriverTestBase;
import eu.stratosphere.pact.runtime.test.util.UniformPactRecordGenerator;
import eu.stratosphere.pact.runtime.test.util.TaskCancelThread;

public class CrossTaskTest extends DriverTestBase<GenericCrosser<PactRecord, PactRecord, PactRecord>>
{
	private static final Log LOG = LogFactory.getLog(CrossTaskTest.class);
	
	private final List<PactRecord> outList = new ArrayList<PactRecord>();

	public CrossTaskTest() {
		super(1*1024*1024);
	}
	
	@Test
	public void testBlock1CrossTask()
	{
		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 100;
		int valCnt2 = 4;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_BLOCKED_OUTER_FIRST);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		try {
			testDriver(testTask, MockCrossStub.class);
		} catch (Exception e) {
			LOG.debug(e);
			Assert.fail("Invoke method caused exception.");
		}
		
		int expCnt = keyCnt1*valCnt1*keyCnt2*valCnt2;
		
		Assert.assertTrue("Resultset size was "+this.outList.size()+". Expected was "+expCnt, this.outList.size() == expCnt);
		
		this.outList.clear();
				
	}
	
	@Test
	public void testBlock2CrossTask() {

		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 100;
		int valCnt2 = 4;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_BLOCKED_OUTER_SECOND);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		try {
			testDriver(testTask, MockCrossStub.class);
		} catch (Exception e) {
			LOG.debug(e);
			Assert.fail("Invoke method caused exception.");
		}
		
		int expCnt = keyCnt1*valCnt1*keyCnt2*valCnt2;
		
		Assert.assertTrue("Resultset size was "+this.outList.size()+". Expected was "+expCnt, this.outList.size() == expCnt);
		
		this.outList.clear();
		
	}
	
	@Test
	public void testFailingBlockCrossTask() {

		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 100;
		int valCnt2 = 4;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_BLOCKED_OUTER_FIRST);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		boolean stubFailed = false;
		
		try {
			testDriver(testTask, MockFailingCrossStub.class);
		} catch (Exception e) {
			stubFailed = true;
		}
		
		Assert.assertTrue("Stub exception was not forwarded.", stubFailed);
		
		this.outList.clear();
				
	}
	
	@Test
	public void testStream1CrossTask() {

		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 100;
		int valCnt2 = 4;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_STREAMED_OUTER_FIRST);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		try {
			testDriver(testTask, MockCrossStub.class);
		} catch (Exception e) {
			LOG.debug(e);
			Assert.fail("Invoke method caused exception.");
		}
		
		int expCnt = keyCnt1*valCnt1*keyCnt2*valCnt2;
		
		Assert.assertTrue("Resultset size was "+this.outList.size()+". Expected was "+expCnt, this.outList.size() == expCnt);
		
		this.outList.clear();
		
	}
	
	@Test
	public void testStream2CrossTask() {

		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 100;
		int valCnt2 = 4;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_STREAMED_OUTER_SECOND);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		try {
			testDriver(testTask, MockCrossStub.class);
		} catch (Exception e) {
			LOG.debug(e);
			Assert.fail("Invoke method caused exception.");
		}
		
		int expCnt = keyCnt1*valCnt1*keyCnt2*valCnt2;
		
		Assert.assertTrue("Resultset size was "+this.outList.size()+". Expected was "+expCnt, this.outList.size() == expCnt);
		
		this.outList.clear();
		
	}
	
	@Test
	public void testStreamEmptyInnerCrossTask() {

		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 0;
		int valCnt2 = 0;

		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_STREAMED_OUTER_FIRST);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		try {
			testDriver(testTask, MockCrossStub.class);
		} catch (Exception e) {
			LOG.debug(e);
			Assert.fail("Invoke method caused exception.");
		}
		
		int expCnt = keyCnt1*valCnt1*keyCnt2*valCnt2;
		
		Assert.assertTrue("Resultset size was "+this.outList.size()+". Expected was "+expCnt, this.outList.size() == expCnt);
		
		this.outList.clear();
		
	}
	
	@Test
	public void testStreamEmptyOuterCrossTask() {

		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 0;
		int valCnt2 = 0;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_STREAMED_OUTER_SECOND);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		try {
			testDriver(testTask, MockCrossStub.class);
		} catch (Exception e) {
			LOG.debug(e);
			Assert.fail("Invoke method caused exception.");
		}
		
		int expCnt = keyCnt1*valCnt1*keyCnt2*valCnt2;
		
		Assert.assertTrue("Resultset size was "+this.outList.size()+". Expected was "+expCnt, this.outList.size() == expCnt);
		
		this.outList.clear();
		
	}
	
	@Test
	public void testBlockEmptyInnerCrossTask() {

		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 0;
		int valCnt2 = 0;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_BLOCKED_OUTER_FIRST);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		try {
			testDriver(testTask, MockCrossStub.class);
		} catch (Exception e) {
			LOG.debug(e);
			Assert.fail("Invoke method caused exception.");
		}
		
		int expCnt = keyCnt1*valCnt1*keyCnt2*valCnt2;
		
		Assert.assertTrue("Resultset size was "+this.outList.size()+". Expected was "+expCnt, this.outList.size() == expCnt);
		
		this.outList.clear();
		
	}
	
	@Test
	public void testBlockEmptyOuterCrossTask() {

		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 0;
		int valCnt2 = 0;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_BLOCKED_OUTER_SECOND);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		try {
			testDriver(testTask, MockCrossStub.class);
		} catch (Exception e) {
			LOG.debug(e);
			Assert.fail("Invoke method caused exception.");
		}
		
		int expCnt = keyCnt1*valCnt1*keyCnt2*valCnt2;
		
		Assert.assertTrue("Resultset size was "+this.outList.size()+". Expected was "+expCnt, this.outList.size() == expCnt);
		
		this.outList.clear();
		
	}
	
	
	
	@Test
	public void testFailingStreamCrossTask() {

		int keyCnt1 = 10;
		int valCnt1 = 1;
		
		int keyCnt2 = 100;
		int valCnt2 = 4;

		super.addInput(new UniformPactRecordGenerator(keyCnt1, valCnt1, false));
		super.addInput(new UniformPactRecordGenerator(keyCnt2, valCnt2, false));
		super.addOutput(this.outList);
		
		CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_STREAMED_OUTER_FIRST);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		boolean stubFailed = false;
		
		try {
			testDriver(testTask, MockFailingCrossStub.class);
		} catch (Exception e) {
			stubFailed = true;
		}
		
		Assert.assertTrue("Stub exception was not forwarded.", stubFailed);
		
		this.outList.clear();
		
	}

	@Test
	public void testCancelBlockCrossTaskInit() {
		
		int keyCnt = 10;
		int valCnt = 1;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt, valCnt, false));
		super.addInput(new DelayingInfinitiveInputIterator(100));
		super.addOutput(this.outList);
		
		final CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_BLOCKED_OUTER_FIRST);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		Thread taskRunner = new Thread() {
			@Override
			public void run() {
				try {
					testDriver(testTask, MockCrossStub.class);
				} catch (Exception ie) {
					ie.printStackTrace();
					Assert.fail("Task threw exception although it was properly canceled");
				}
			}
		};
		taskRunner.start();
		
		TaskCancelThread tct = new TaskCancelThread(1, taskRunner, this);
		tct.start();
		
		try {
			tct.join();
			taskRunner.join();		
		} catch(InterruptedException ie) {
			Assert.fail("Joining threads failed");
		}
		
	}
	
	@Test
	public void testCancelBlockCrossTaskCrossing() {
		
		int keyCnt = 10;
		int valCnt = 1;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt, valCnt, false));
		super.addInput(new DelayingInfinitiveInputIterator(100));
		super.addOutput(this.outList);
		
		final CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_BLOCKED_OUTER_SECOND);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		Thread taskRunner = new Thread() {
			@Override
			public void run() {
				try {
					testDriver(testTask, MockCrossStub.class);
				} catch (Exception ie) {
					ie.printStackTrace();
					Assert.fail("Task threw exception although it was properly canceled");
				}
			}
		};
		taskRunner.start();
		
		TaskCancelThread tct = new TaskCancelThread(1, taskRunner, this);
		tct.start();
		
		try {
			tct.join();
			taskRunner.join();		
		} catch(InterruptedException ie) {
			Assert.fail("Joining threads failed");
		}
		
	}
	
	@Test
	public void testCancelStreamCrossTaskInit() {
		
		int keyCnt = 10;
		int valCnt = 1;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt, valCnt, false));
		super.addInput(new DelayingInfinitiveInputIterator(100));
		super.addOutput(this.outList);
		
		final CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_STREAMED_OUTER_FIRST);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		Thread taskRunner = new Thread() {
			@Override
			public void run() {
				try {
					testDriver(testTask, MockCrossStub.class);
				} catch (Exception ie) {
					ie.printStackTrace();
					Assert.fail("Task threw exception although it was properly canceled");
				}
			}
		};
		taskRunner.start();
		
		TaskCancelThread tct = new TaskCancelThread(1, taskRunner, this);
		tct.start();
		
		try {
			tct.join();
			taskRunner.join();		
		} catch(InterruptedException ie) {
			Assert.fail("Joining threads failed");
		}
		
	}
	
	@Test
	public void testCancelStreamCrossTaskCrossing() {
		
		int keyCnt = 10;
		int valCnt = 1;
		
		super.addInput(new UniformPactRecordGenerator(keyCnt, valCnt, false));
		super.addInput(new DelayingInfinitiveInputIterator(100));
		super.addOutput(this.outList);
		
		final CrossDriver<PactRecord, PactRecord, PactRecord> testTask = new CrossDriver<PactRecord, PactRecord, PactRecord>();
		super.getTaskConfig().setLocalStrategy(LocalStrategy.NESTEDLOOP_STREAMED_OUTER_SECOND);
		super.getTaskConfig().setMemorySize(1 * 1024 * 1024);
		
		Thread taskRunner = new Thread() {
			@Override
			public void run() {
				try {
					testDriver(testTask, MockCrossStub.class);
				} catch (Exception ie) {
					ie.printStackTrace();
					Assert.fail("Task threw exception although it was properly canceled");
				}
			}
		};
		taskRunner.start();
		
		TaskCancelThread tct = new TaskCancelThread(1, taskRunner, this);
		tct.start();
		
		try {
			tct.join();
			taskRunner.join();		
		} catch(InterruptedException ie) {
			Assert.fail("Joining threads failed");
		}
		
	}
	
	public static class MockCrossStub extends CrossStub {

		@Override
		public void cross(PactRecord record1, PactRecord record2, Collector<PactRecord> out) {
			
			out.collect(record1);
		}
	}
	
	public static class MockFailingCrossStub extends CrossStub {

		int cnt = 0;
		
		@Override
		public void cross(PactRecord record1, PactRecord record2, Collector<PactRecord> out) {
			
			if(++this.cnt>=10) {
				throw new RuntimeException("Expected Test Exception");
			}
						
			out.collect(record1);			
		}
	}
	
	
	
}
