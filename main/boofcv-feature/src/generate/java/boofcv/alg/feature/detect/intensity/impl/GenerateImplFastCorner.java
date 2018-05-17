/*
 * Copyright (c) 2011-2018, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.feature.detect.intensity.impl;

import boofcv.misc.AutoTypeImage;
import boofcv.misc.CircularIndex;
import boofcv.misc.CodeGeneratorBase;
import org.ddogleg.struct.FastQueue;

import java.io.FileNotFoundException;

/**
 * @author Peter Abeles
 */
public class GenerateImplFastCorner extends CodeGeneratorBase {

	private static final int TOTAL_CIRCLE = 16;

	// minimum number of edge points in a row to make a corner
	private int minContinuous;

	AutoTypeImage imageType;
	String sumType;
	String bitwise;
	String dataType;

	boolean useElse;
	int tabs;

	public GenerateImplFastCorner() {
		super(false);
	}

	@Override
	public void generate() throws FileNotFoundException {
//		int n[] = {9,10,11,12};
//		AutoTypeImage d[] = {AutoTypeImage.U8,AutoTypeImage.F32};

		int n[] = {9};
		AutoTypeImage d[] = {AutoTypeImage.U8};

		for( int minContinuous : n ) {
			for( AutoTypeImage imageType : d ) {
				createFile(imageType,minContinuous);
			}
		}
	}

	public void createFile( AutoTypeImage imageType , int minContinuous ) throws FileNotFoundException {
		className = "ImplFastCorner"+minContinuous+"_"+imageType.getAbbreviatedType();

		this.imageType = imageType;
		this.sumType = imageType.getSumType();
		this.bitwise = imageType.getBitWise();
		this.dataType = imageType.getDataType();
		this.minContinuous = minContinuous;

		initFile();
		printPreamble();
		printCheck();

		out.println("}");
	}


	private void printPreamble() throws FileNotFoundException {
		out.print(
				"/**\n" +
				" * <p>\n" +
				" * Contains logic for detecting fast corners. Pixels are sampled such that they can eliminate the most\n" +
				" * number of possible corners, reducing the number of samples required.\n" +
				" * </p>\n" +
				" *\n" +
				" * <p>\n" +
				" * DO NOT MODIFY. Generated by "+getClass().getSimpleName()+".\n" +
				" * </p>\n" +
				" *\n" +
				" * @author Peter Abeles\n" +
				" */\n" +
				"public class "+className+"\n" +
				"{\n" +
				"\tprotected int offsets[];\n" +
				"\tprotected "+sumType+" minValue;\n"+
				"\tprotected "+sumType+" maxValue;\n"+
				"\tprotected "+sumType+" tol;\n"+
				"\tprivate "+sumType+" lower;\n"+
				"\tprivate "+sumType+" upper;\n\n");
	}

	private void printCheck() {

		out.print(
				"\t/**\n" +
				"\t * @return 1 = positive corner, 0 = no corner, -1 = negative corner\n" +
				"\t */\n" +
				"\tprotected int checkCorner( "+dataType+" data[], int index )\n" +
				"\t{\n" +
				"\t\t"+sumType+" lower = Math.max(minValue,(data[index]"+bitwise+") - tol);\n"+
				"\t\t"+sumType+" upper = Math.min(maxValue,(data[index]"+bitwise+") + tol);\n"+
				"\n");

		print();

		out.print("\t}\n\n");
	}

	private void print() {
		FastQueue<Set> queue = new FastQueue<>(Set.class,true);

		Set active = queue.grow();
		active.start = 0;
		active.complete = false;
		active.upper = true;
		active.tryTail = true;

		useElse = false;

		tabs = 2;
		while( queue.size > 0 ) {
			active = queue.getTail();

			// See if it's done or switching the check on bit zero
			if( queue.size == 1 && active.stop < 0 ) {
				if( active.upper ) {
					active.start = active.stop = 0;
					active.upper = false;
					active.tryTail = true;
					useElse = true;
				} else {
					break;
				}
			}
			if( active.complete ) {
				undoThenForwards(queue, active);
			} else {
				int bit = active.start > active.stop ? active.start : active.stop;
				printIf(useElse, tabs++, bit, active.upper);

				if( active.length() == minContinuous ) {
					printReturn(--tabs,active.upper?1:-1);
					if( queue.size == 1 && active.stop >= 0 ) {
						if (active.tryTail) {
							// if it hasn't tried the bits on the tail do that now
							useElse = true;
							active.tryTail = false;
							active.stop -= 1;
							active.start = TOTAL_CIRCLE - 1;
						} else {
							// close all the tail if statements
							for (int i = active.stop; i < minContinuous - 2; i++) {
								printCloseIf(tabs--);
							}
							if (possibleToComplete(active.stop + 1)) {
								// continue moving forward with a new assumption
								useElse = true;
								Set next = queue.grow();
								next.start = next.stop = active.stop + 1;
								next.complete = false;
								next.upper = !active.upper;

								active.tryTail = false;
								active.start = TOTAL_CIRCLE - 1;
								active.stop -= 1;
							} else {
								printCloseIf(tabs--);
								active.tryTail = false;
								active.stop -= 1;
								active.start = TOTAL_CIRCLE - 1;
							}
						}
					} else {
						active.complete = true;
						undoThenForwards(queue, active);
					}
				} else if( active.stop >= active.start ){
					useElse = false;
					active.stop += 1;
				} else {
					useElse = false;
					active.start -= 1;
				}
			}
		}
		printCloseIf(2);
		printReturn(1,0);
		System.out.println("Done");
	}

	private void undoThenForwards(FastQueue<Set> queue, Set active) {
		while( !possibleToComplete(active.stop) && active.stop >= active.start ) {
			printCloseIf(tabs--);
			active.stop -= 1;
		}
		if( possibleToComplete(active.stop) && active.stop > active.start ) {
			Set next = queue.grow();
			next.start = next.stop = active.stop;
			next.complete = false;
			next.upper = !active.upper;
			useElse = true;
			active.stop -= 1;
		} else {
			useElse = true;
			printCloseIf(tabs--);
			queue.removeTail();
		}
	}

	private boolean possibleToComplete( int location ) {
		return (16-location) >= minContinuous;
	}

	private void printIf( boolean isElse, int numTabs , int bit,  boolean upper ) {
		String comparison = upper ? "> upper" : "< lower";
		String strElse = isElse ? "} else " : "";
		out.println(tabs(numTabs)+strElse+"if( "+readBit(bit)+" "+comparison+" ) {");
	}

	private void printReturn( int numTabs , int value ) {
		out.println(tabs(numTabs+1)+"return "+value+";");
	}

	private String readBit( int bit ) {
		return "(data[offsets["+bit+"]]"+bitwise+")";
	}

	private void printCloseIf( int numTabs ) {
		out.println(tabs(numTabs)+"}");
	}

	/**
	 * Prints tabs to ensure proper formatting
	 */
	private String tabs( int depth ) {
		String ret = "";
		for( int i = 0; i < depth; i++ ) {
			ret += "\t";
		}
		return ret;
	}

	enum StateAction {
		FORWARDS,
		BACKWARDS
	}

	public static class Set {
		int start,stop;
		boolean upper;
		boolean complete;
		boolean tryTail;

		public int length() {
			return CircularIndex.distanceP(start,stop,TOTAL_CIRCLE)+1;
		}
	}

	public static void main( String args[] ) throws FileNotFoundException {
		GenerateImplFastCorner gen = new GenerateImplFastCorner();
		gen.generate();
	}

}
