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
package eu.stratosphere.pact.testing;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.nephele.configuration.GlobalConfiguration;
import eu.stratosphere.nephele.io.Reader;
import eu.stratosphere.nephele.io.channels.AbstractInputChannel;
import eu.stratosphere.nephele.services.iomanager.SerializationFactory;
import eu.stratosphere.nephele.services.memorymanager.MemoryAllocationException;
import eu.stratosphere.pact.common.io.InputFormat;
import eu.stratosphere.pact.common.io.SequentialOutputFormat;
import eu.stratosphere.pact.common.type.Key;
import eu.stratosphere.pact.common.type.KeyValuePair;
import eu.stratosphere.pact.common.type.Value;
import eu.stratosphere.pact.common.util.FormatUtil;
import eu.stratosphere.pact.runtime.serialization.WritableSerializationFactory;
import eu.stratosphere.pact.runtime.sort.UnilateralSortMerger;
import eu.stratosphere.pact.runtime.task.ReduceTask;
import eu.stratosphere.pact.runtime.task.util.TaskConfig;

/**
 * Represents the input or output values of a {@link TestPlan}. The class is
 * especially important when setting the expected values in the TestPlan.<br>
 * <br>
 * There are two ways to specify the values:
 * <ol>
 * <li>From a file: with {@link #fromFile(Class, String)} and {@link #fromFile(Class, String, Configuration)} the
 * location, format, and configuration of the data can be specified. The file is lazily loaded and thus can be
 * comparable large.
 * <li>Ad-hoc: key/value pairs can be added with {@link #add(Key, Value)}, {@link #add(KeyValuePair...)}, and
 * {@link #add(Iterable)}. Please note that the actual amount of pairs is quite for a test case as the TestPlan already
 * involves a certain degree of overhead.<br>
 * <br>
 * TestPairs are directly comparable with equals and hashCode based on its content. Please note that in the case of
 * large file-based TestPairs, the time needed to compute the {@link #hashCode()} or to compare two instances with
 * {@link #equals(Object)} can become quite long. Currently, the comparison result is order-dependent as TestPairs are
 * interpreted as a list.<br>
 * <br>
 * Currently there is no notion of an empty set of pairs.
 * 
 * @author Arvid Heise
 * @param <K>
 *        the type of the keys
 * @param <V>
 *        the type of the values
 */
public class TestPairs<K extends Key, V extends Value> implements
		Iterable<KeyValuePair<K, V>>, Closeable {
	private static final class TestPairsReader<K extends Key, V extends Value>
			implements Reader<KeyValuePair<K, V>> {
		KeyValuePair<K, V> currentPair;

		private final InputFileIterator<K, V> inputFileIterator;

		private TestPairsReader(
				final InputFileIterator<K, V> inputFileIterator,
				final KeyValuePair<K, V> actualPair) {
			this.inputFileIterator = inputFileIterator;
			this.currentPair = actualPair;
		}

		@Override
		public List<AbstractInputChannel<KeyValuePair<K, V>>> getInputChannels() {
			return null;
		}

		@Override
		public boolean hasNext() {
			return this.currentPair != null;
		}

		@Override
		public KeyValuePair<K, V> next() throws IOException,
				InterruptedException {
			if (!this.hasNext())
				throw new NoSuchElementException();
			final KeyValuePair<K, V> current = this.currentPair;
			if (this.inputFileIterator.hasNext())
				this.currentPair = this.inputFileIterator.next();
			else
				this.currentPair = null;
			return current;
		}
	}

	private static final Iterator<KeyValuePair<Key, Value>> EMPTY_ITERATOR = new ArrayList<KeyValuePair<Key, Value>>()
		.iterator();

	private Configuration configuration;

	private Class<? extends InputFormat<K, V>> inputFormatClass;

	private final List<KeyValuePair<K, V>> pairs = new ArrayList<KeyValuePair<K, V>>();

	private String path;

	private ClosableManager closableManager = new ClosableManager();

	private boolean empty;

	private boolean isEmpty() {
		return this.empty;
	}

	private void setEmpty(boolean empty) {
		this.empty = empty;
	}

	/**
	 * Specifies that the set of key/value pairs is empty. This method is primarily used to distinguish between an empty
	 * uninitialized set and a set deliberately left empty. Further calls to {@link #fromFile(Class, String)} or
	 * {@link #add(Iterable)} will reset the effect of this method invocation and vice-versa.
	 */
	public void setEmpty() {
		setEmpty(true);
	}

	/**
	 * Adds several pairs at once.
	 * 
	 * @param pairs
	 *        the pairs to add
	 * @return this
	 */
	public TestPairs<K, V> add(final Iterable<KeyValuePair<K, V>> pairs) {
		for (final KeyValuePair<K, V> pair : pairs)
			this.pairs.add(pair);
		setEmpty(false);
		return this;
	}

	/**
	 * Adds a pair consisting of the given key and value.
	 * 
	 * @param key
	 *        the key of the pair
	 * @param value
	 *        the value of the pair
	 * @return this
	 */
	public TestPairs<K, V> add(final K key, final V value) {
		this.pairs.add(new KeyValuePair<K, V>(key, value));
		setEmpty(false);
		return this;
	}

	/**
	 * Adds several pairs at once.
	 * 
	 * @param pairs
	 *        the pairs to add
	 * @return this
	 */
	public TestPairs<K, V> add(final KeyValuePair<K, V>... pairs) {
		for (final KeyValuePair<K, V> pair : pairs)
			this.pairs.add(pair);
		setEmpty(false);
		return this;
	}

	private void assignMemory(final TaskConfig config, final int memSize) {
		// this code currently has a very simple way of assigning space to the
		// I/O and sort buffers
		// in future releases, we plan to design the sort-component in a more
		// adaptive fashion,
		// making it distribute its memory among sort and merge space by itself

		int ioMem, sortMem, numSortBuffers, sortBufferSize;

		// decide how to divide the memory between sort and I/O space
		if (memSize > 512)
			ioMem = MAX_IO_BUFFER_SIZE;
		else if (memSize > 64)
			ioMem = memSize / 32;
		else if (memSize > 32)
			ioMem = 2;
		else if (memSize > MIN_SORT_HEAP + MIN_IO_BUFFER_SIZE)
			ioMem = MIN_IO_BUFFER_SIZE;
		else
			throw new OutOfMemoryError();
		sortMem = memSize - ioMem;

		// decide how to divide the sort memory among different buffers
		if (sortMem > 3 * MAX_SORT_HEAP_BUFFER_SIZE) {
			numSortBuffers = sortMem / MAX_SORT_HEAP_BUFFER_SIZE + 1;
			// correct rounding loss
			numSortBuffers = sortMem / (sortMem / numSortBuffers);
		} else if (sortMem > 3 * 64)
			numSortBuffers = 3;
		else if (sortMem >= 2 * MIN_SORT_HEAP)
			numSortBuffers = 2;
		else
			numSortBuffers = 1;
		sortBufferSize = sortMem / numSortBuffers;

		// set the config
		config.setIOBufferSize(ioMem);
		config.setMergeFactor(DEFAUTL_MERGE_FACTOR);
		config.setNumSortBuffer(numSortBuffers);
		config.setSortBufferSize(sortBufferSize);
	}

	/**
	 * Uses {@link UnilateralSortMerger} to sort the files of the {@link InputFileIterator}.
	 */
	@SuppressWarnings("unchecked")
	private Iterator<KeyValuePair<K, V>> createSortedIterator(
			final InputFileIterator<K, V> inputFileIterator) {
		final KeyValuePair<K, V> actualPair = inputFileIterator.next();

		final TaskConfig config = new TaskConfig(
				GlobalConfiguration.getConfiguration());
		this.assignMemory(config, 10);

		// set up memory and io parameters
		final int NUM_SORT_BUFFERS = config.getNumSortBuffer();
		final int SIZE_SORT_BUFFER = config.getSortBufferSize() * 1024 * 1024;
		final int MEMORY_IO = config.getIOBufferSize() * 1024 * 1024;
		final int MAX_NUM_FILEHANLDES = config.getMergeFactor();

		// create a key comparator
		final Comparator<K> keyComparator = new Comparator<K>() {
			@Override
			public int compare(final K k1, final K k2) {
				return k1.compareTo(k2);
			}
		};

		try {
			// obtain key serializer
			final SerializationFactory<K> keySerialization = new WritableSerializationFactory<K>(
					(Class<K>) actualPair.getKey().getClass());
			// obtain value serializer
			final SerializationFactory<V> valSerialization = new WritableSerializationFactory<V>(
					(Class<V>) actualPair.getValue().getClass());

			// instantiate a sort-merger
			@SuppressWarnings("rawtypes")
			final UnilateralSortMerger<K, V> sortMerger = new UnilateralSortMerger<K, V>(
					MockTaskManager.INSTANCE.getMemoryManager(),
					MockTaskManager.INSTANCE.getIoManager(), NUM_SORT_BUFFERS,
					SIZE_SORT_BUFFER, MEMORY_IO, MAX_NUM_FILEHANLDES,
					keySerialization, valSerialization, keyComparator,
					new TestPairsReader(inputFileIterator, actualPair), 0.5f,
					new ReduceTask());

			this.closableManager.add(new Closeable() {
				@Override
				public void close() throws IOException {
					try {
						sortMerger.shutdown();
					} catch (InterruptedException e) {
					}
				}
			});
			// obtain and return a grouped iterator from the sort-merger
			return sortMerger.getIterator();
		} catch (final MemoryAllocationException mae) {
			throw new RuntimeException(
					"MemoryManager is not able to provide the required amount of memory for ReduceTask",
					mae);
		} catch (final IOException ioe) {
			throw new RuntimeException(
					"IOException caught when obtaining SortMerger for ReduceTask",
					ioe);
		}
	}

	@Override
	public void close() throws IOException {
		this.closableManager.close();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		final TestPairs<K, V> other = (TestPairs<K, V>) obj;

		final Iterator<KeyValuePair<K, V>> thisIterator = this.iterator();
		final Iterator<KeyValuePair<K, V>> otherIterator = other.iterator();
		while (thisIterator.hasNext() && otherIterator.hasNext())
			if (!thisIterator.next().equals(otherIterator.next()))
				return false;

		return thisIterator.hasNext() == otherIterator.hasNext();
	}

	/**
	 * Initializes this {@link TestPairs} from the given file.
	 * 
	 * @param inputFormatClass
	 *        the class of the {@link InputFormat}
	 * @param file
	 *        the path to the file, can be relative
	 * @return this
	 */
	@SuppressWarnings("rawtypes")
	public TestPairs<K, V> fromFile(
			final Class<? extends InputFormat> inputFormatClass,
			final String file) {
		this.fromFile(inputFormatClass, file, new Configuration());
		return this;
	}

	/**
	 * Initializes this {@link TestPairs} from the given file.
	 * 
	 * @param inputFormatClass
	 *        the class of the {@link InputFormat}
	 * @param file
	 *        the path to the file, can be relative
	 * @param configuration
	 *        the configuration for the {@link InputFormat}.
	 * @return this
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public TestPairs<K, V> fromFile(
			final Class<? extends InputFormat> inputFormatClass,
			final String file, final Configuration configuration) {
		this.path = file;
		this.inputFormatClass = (Class<? extends InputFormat<K, V>>) inputFormatClass;
		this.configuration = configuration;
		setEmpty(false);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		final Iterator<KeyValuePair<K, V>> iterator = this.iterator();
		while (iterator.hasNext())
			result = prime * result + iterator.next().hashCode();
		return result;
	}

	/**
	 * Returns true if any add method has been called at least one.
	 * 
	 * @return true if pairs were specified in an ad-hoc manner
	 */
	public boolean isAdhoc() {
		return !this.pairs.isEmpty();
	}

	/**
	 * Returns true if either pairs were added manually or with {@link #fromFile(Class, String, Configuration)}.
	 * 
	 * @return true if either pairs were added manually or with {@link #fromFile(Class, String, Configuration)}.
	 */
	public boolean isInitialized() {
		return this.isEmpty() || !this.pairs.isEmpty() || this.inputFormatClass != null;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<KeyValuePair<K, V>> iterator() {
		if (isEmpty())
			return (Iterator) EMPTY_ITERATOR;

		if (!this.isAdhoc() && this.inputFormatClass != null) {

			final InputFileIterator<K, V> inputFileIterator;
			try {
				inputFileIterator = new InputFileIterator<K, V>(
						!this.needsSorting(), FormatUtil.createInputFormats(
								this.inputFormatClass, this.path,
								this.configuration));
			} catch (final IOException e) {
				TestPlan.fail(e, "reading expected values");
				return null;
			} catch (final Exception e) {
				TestPlan.fail(e, "creating input format");
				return null;
			}

			if (!inputFileIterator.hasNext() || !this.needsSorting())
				return inputFileIterator;

			return this.createSortedIterator(inputFileIterator);
		}
		Collections.sort(this.pairs, new Comparator<KeyValuePair<K, V>>() {
			@Override
			public int compare(KeyValuePair<K, V> o1, KeyValuePair<K, V> o2) {
				return o1.getKey().compareTo(o2.getKey());
			}
		});
		return this.pairs.iterator();
	}

	private boolean needsSorting() {
		return true;
	}

	/**
	 * Saves the data to the given path in an internal format.
	 * 
	 * @param path
	 *        the path to write to, may be relative
	 * @throws IOException
	 *         if an I/O error occurred
	 */
	@SuppressWarnings("unchecked")
	public void saveToFile(final String path) throws IOException {
		final SequentialOutputFormat outputFormat = FormatUtil
				.createOutputFormat(SequentialOutputFormat.class, path, null);

		final Iterator<KeyValuePair<K, V>> iterator = this.iterator();
		while (iterator.hasNext())
			outputFormat.writePair((KeyValuePair<Key, Value>) iterator.next());
	}

	@Override
	public String toString() {
		final StringBuilder stringBuilder = new StringBuilder("TestPairs: ");
		final Iterator<KeyValuePair<K, V>> iterator = this.iterator();
		for (int index = 0; index < 10 && iterator.hasNext(); index++) {
			if (index > 0)
				stringBuilder.append("; ");
			stringBuilder.append(iterator.next());
		}
		if (iterator.hasNext())
			stringBuilder.append("...");
		return stringBuilder.toString();
	}

	private static final int DEFAUTL_MERGE_FACTOR = 64; // the number of streams
														// to merge at once

	private static final int MAX_IO_BUFFER_SIZE = 16;

	private static final int MAX_SORT_HEAP_BUFFER_SIZE = 2047;

	private static final int MIN_IO_BUFFER_SIZE = 1;

	private static final int MIN_SORT_HEAP = 4;
}