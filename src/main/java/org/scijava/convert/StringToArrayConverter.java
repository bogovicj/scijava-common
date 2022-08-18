/*
 * #%L
 * SciJava Common shared library for SciJava software.
 * %%
 * Copyright (C) 2009 - 2022 SciJava developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.convert;

import org.scijava.Priority;
import org.scijava.parsington.ExpressionParser;
import org.scijava.parsington.SyntaxTree;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.Types;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@link Converter} that specializes in converting {@link String}s to
 * n-dimensional arrays. This {@link Converter} can convert any array whose
 * component types can be created from a {@link String}. By default, this
 * {@link Converter} delimits the {@link String} based on commas.
 * 
 * @author Gabriel Selzer
 */
@Plugin(type = Converter.class, priority = Priority.VERY_LOW)
public class StringToArrayConverter extends AbstractConverter<String, Object> {

	@Parameter
	private ConvertService convertService;

	private final ExpressionParser parser = new ExpressionParser();

	@Override
	public boolean canConvert(final Class<?> src, final Class<?> dest) {
		if (src == null) return false;
		final Class<?> saneSrc = Types.box(src);
		final Class<?> saneDest = Types.box(dest);
		return saneSrc == String.class && saneDest.isArray();
	}

	@Override
	public boolean canConvert(final Object src, final Type dest) {
		return canConvert(src, Types.raw(dest));
	}

	@Override
	public boolean canConvert(final Object src, final Class<?> dest) {

		// First, ensure the base types conform
		if (!canConvert(src.getClass(), dest)) return false;
		// Then, ensure we can parse the string
		SyntaxTree tree;
		try {
			tree = parser.parseTree((String) src);
		}
		catch (IllegalArgumentException e) {
			return false;
		}
		// We can always convert empty arrays as we don't have to create Objects
		if (tree.count() == 0) return true;
		// Finally, ensure that we can convert the elements of the array.
		// NB this check is merely a heuristic. In the case of a heterogeneous
		// array, canConvert may falsely return positive, if later elements in the
		// string-ified array cannot be converted into Objects. We make this
		// compromise in the interest of speed, however, as ensuring correctness
		// would require a premature conversion of the entire array.
		Object testSrc = firstElement(tree);
		Class<?> testDest = unitComponentType(dest);
		return convertService.supports(testSrc, testDest);
	}

	@Override
	public Object convert(Object src, Type dest) {
		final Type componentType = Types.component(dest);
		if (componentType == null) {
			throw new IllegalArgumentException(dest + " is not an array type!");
		}
		try {
			SyntaxTree tree = parser.parseTree((String) src);
			return convertToArray(tree, Types.raw(componentType));
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T convert(Object src, Class<T> dest) {
		return (T) convert(src, (Type) dest);
	}

	@Override
	public Class<Object> getOutputType() {
		return Object.class;
	}

	@Override
	public Class<String> getInputType() {
		return String.class;
	}

	// -- HELPER METHODS -- //

	/**
	 * Converts {@code src} into an array of component type {@code componentType}
	 * 
	 * @param tree the {@link String} to convert
	 * @param componentType the component type of the output array
	 * @return an array of {@code componentType} whose elements were created from
	 *         {@code src}
	 */
	private Object convertToArray(SyntaxTree tree, final Class<?> componentType) {
		// Create the array
		final Object array = Array.newInstance(componentType, tree.count());
		// Set each element of the array
		for (int i = 0; i < tree.count(); i++) {
			SyntaxTree subTree = tree.child(i);
			Object element;
			// Case 1: Element is an array
			if (componentType.isArray()) {
				element = convertToArray(subTree, componentType.getComponentType());
			}
			// Case 2: Element is a single object
			else {
				element = convertService.convert(subTree.token(), componentType);
			}
			Array.set(array, i, element);
		}
		return array;
	}

	/**
	 * Similar to {@link Class#getComponentType()}, but handles nested array types
	 *
	 * @param c the {@link Class} that may be an array class
	 * @return the <em>unit</em> component type of {@link Class} {@code c}
	 */
	private Class<?> unitComponentType(Class<?> c) {
		if (!c.isArray()) return c;
		return unitComponentType(c.getComponentType());
	}

	/**
	 * Traverses {@code tree} to find the first element
	 * 
	 * @param tree the {@link SyntaxTree} containing elements
	 * @return the first {@link Object} in {@code tree}
	 */
	private Object firstElement(SyntaxTree tree) {
		while (tree.count() > 0) {
			tree = tree.child(0);
		}
		return tree.token();
	}

}
