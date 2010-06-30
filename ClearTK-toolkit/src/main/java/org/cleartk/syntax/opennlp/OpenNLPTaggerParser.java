/** 
 * Copyright (c) 2007-2008, Regents of the University of Colorado 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 * Neither the name of the University of Colorado at Boulder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE. 
 */
package org.cleartk.syntax.opennlp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import opennlp.maxent.MaxentModel;
import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.tools.lang.english.HeadRules;
import opennlp.tools.lang.english.ParserChunker;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserTagger;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.Span;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.syntax.treebank.type.TopTreebankNode;
import org.cleartk.syntax.treebank.type.TreebankNode;
import org.cleartk.type.Sentence;
import org.cleartk.type.Token;
import org.cleartk.util.AnnotationRetrieval;
import org.uimafit.component.initialize.ConfigurationParameterInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;

/**
 * <br>
 * Copyright (c) 2010, Regents of the University of Colorado <br>
 * All rights reserved.
 * 
 * 
 * @author Philipp Wetzler
 */
public class OpenNLPTaggerParser extends JCasAnnotator_ImplBase {

	public static final String PARAM_BUILD_MODEL_FILE = 
			ConfigurationParameterFactory.createConfigurationParameterName(OpenNLPTaggerParser.class, "buildModelFile");
	@ConfigurationParameter(
			mandatory = true,
			description = "provides the path of the OpenNLP parser model build file, e.g. resources/models/OpenNLP.Parser.English.Build.bin.gz.  See javadoc for opennlp.tools.parser.chunking.Parser.")
	private String buildModelFile;

	public static final String PARAM_CHECK_MODEL_FILE = 
			ConfigurationParameterFactory.createConfigurationParameterName(OpenNLPTaggerParser.class, "checkModelFile");
	@ConfigurationParameter(
			mandatory = true,
			description = "provides the path of the OpenNLP parser model check file, e.g. resources/models/OpenNLP.Parser.English.Check.bin.gz. See javadoc for opennlp.tools.parser.chunking.Parser.")
	private String checkModelFile;

	public static final String PARAM_CHUNK_MODEL_FILE = 
			ConfigurationParameterFactory.createConfigurationParameterName(OpenNLPTaggerParser.class, "chunkModelFile");
	@ConfigurationParameter(
			mandatory = true,
			description = "provides the path of the OpenNLP chunker model file, e.g. resources/models/OpenNLP.Chunker.English.bin.gz. See javadoc for opennlp.tools.lang.english.ParserChunker.")
	private String chunkModelFile;

	public static final String PARAM_HEAD_RULES_FILE = 
			ConfigurationParameterFactory.createConfigurationParameterName(OpenNLPTaggerParser.class, "headRulesFile");
	@ConfigurationParameter(
			mandatory = true,
			description = "provides the path of the OpenNLP head rules file, e.g. resources/models/OpenNLP.HeadRules.txt. See javadoc for opennlp.tools.lang.english.HeadRules.")
	private String headRulesFile;

	public static final String PARAM_BEAM_SIZE = 
			ConfigurationParameterFactory.createConfigurationParameterName(OpenNLPTaggerParser.class, "beamSize");
	@ConfigurationParameter(
			mandatory = true,
			defaultValue = ""+Parser.defaultBeamSize,
			description = "indicates the beam size that should be used in the parser's search.  See javadoc for opennlp.tools.parser.chunking.Parser.")
	private int beamSize;

	public static final String PARAM_ADVANCE_PERCENTAGE = 
			ConfigurationParameterFactory.createConfigurationParameterName(OpenNLPTaggerParser.class, "advancePercentage");
	@ConfigurationParameter(
			mandatory = true,
			defaultValue = ""+Parser.defaultAdvancePercentage,
			description = "indicates \"the amount of probability mass required of advanced outcomes\".  See javadoc for opennlp.tools.parser.chunking.Parser.")
	private float advancePercentage;

	public static final String PARAM_POSTAG_MODEL_FILE = 
			ConfigurationParameterFactory.createConfigurationParameterName(OpenNLPTaggerParser.class, "postagModelFile");
	@ConfigurationParameter(
			mandatory = true, 
			description = "provides the path of the OpenNLP part-of-speech tagger model file, e.g.  resources/models/OpenNLP.POSTags.English.bin.gz.  See javadoc for opennlp.maxent.io.SuffixSensitiveGISModelReader.")
	private String postagModelFile;

	public static final String PARAM_POSTAG_DICTIONARY_FILE = 
			ConfigurationParameterFactory.createConfigurationParameterName(	OpenNLPTaggerParser.class, "postagDictionaryFile");
	@ConfigurationParameter(
			mandatory = true, 
			description = "provides the path of the OpenNLP part-of-speech tagger dictionary file, e.g. resources/models/OpenNLP.TagDict.txt.  See javadoc for opennlp.tools.postag.POSDictionary.")
	private String postagDictionaryFile;

	public static final String PARAM_CASE_SENSITIVE = 
			ConfigurationParameterFactory.createConfigurationParameterName(OpenNLPTaggerParser.class, "caseSensitive");
	@ConfigurationParameter(
			defaultValue = "true", 
			description = "when false indicates that the POSDictionary should ignore case.  See javadoc for opennlp.tools.postag.POSDictionary.")
	private boolean caseSensitive;

	protected Parser parser;

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		String text = jCas.getDocumentText();

		FSIterator<Annotation> sentences = jCas.getJFSIndexRepository().getAnnotationIndex(Sentence.type).iterator();
		while (sentences.hasNext()) {
			Sentence sentence = (Sentence) sentences.next();

			Parse parse = new Parse(text, new Span(sentence.getBegin(), sentence.getEnd()), Parser.INC_NODE, 1, null);

			List<Token> tokenList = AnnotationRetrieval.getAnnotations(jCas, sentence, Token.class);
			for (Token token : tokenList) {
				parse.insert(new Parse(text, new Span(token.getBegin(), token.getEnd()), Parser.TOK_NODE, 0, 0));
			}

			parse = this.parser.parse(parse);

			// if the sentence was successfully parsed, add the tree to the
			// sentence
			if (parse.getType() == Parser.TOP_NODE) {
				TopTreebankNode topNode = (TopTreebankNode) buildAnnotation(parse, jCas);
				topNode.addToIndexes();
			}
			
			// add the POS tags to the tokens
			setPOSTags(parse, tokenList.iterator(), jCas);
		}
	}

	protected static TreebankNode buildAnnotation(Parse p, JCas jCas) {
		TreebankNode myNode;
		if (p.getType() == Parser.TOP_NODE) {
			TopTreebankNode topNode = new TopTreebankNode(jCas);
			topNode.setParent(null);

			StringBuffer sb = new StringBuffer();
			p.show(sb);
			topNode.setTreebankParse(sb.toString());

			myNode = topNode;
		}
		else {
			myNode = new TreebankNode(jCas);
		}

		myNode.setNodeType(p.getType());
		myNode.setBegin(p.getSpan().getStart());
		myNode.setEnd(p.getSpan().getEnd());

		if (p.getChildCount() == 1 && p.getChildren()[0].getType() == Parser.TOK_NODE) {
			myNode.setLeaf(true);
			myNode.setNodeValue(p.getChildren()[0].toString());
			myNode.setChildren(new FSArray(jCas, 0));
		}
		else {
			myNode.setNodeValue(null);
			myNode.setLeaf(false);

			List<FeatureStructure> cArray = new ArrayList<FeatureStructure>(p.getChildCount());

			for (Parse cp : p.getChildren()) {
				TreebankNode cNode = buildAnnotation(cp, jCas);
				cNode.setParent(myNode);
				cNode.addToIndexes();
				cArray.add(cNode);
			}

			FSArray cFSArray = new FSArray(jCas, cArray.size());
			cFSArray.copyFromArray(cArray.toArray(new FeatureStructure[cArray.size()]), 0, 0, cArray.size());
			myNode.setChildren(cFSArray);
		}

		if (p.getType() == Parser.TOP_NODE) {
			List<TreebankNode> tList = getTerminals(myNode);
			FSArray tfsa = new FSArray(jCas, tList.size());
			tfsa.copyFromArray(tList.toArray(new FeatureStructure[tList.size()]), 0, 0, tList.size());
			((TopTreebankNode) myNode).setTerminals(tfsa);
		}
		return myNode;
	}
	
	protected static void setPOSTags(Parse p, Iterator<Token> tokenIterator, JCas view) {
		if( p.isPosTag() ) {
			Token t = tokenIterator.next();
			t.setPos(p.getType());
		} else {
			for( Parse child : p.getChildren() ) {
				setPOSTags(child, tokenIterator, view);
			}
		}
	}

	protected static List<TreebankNode> getTerminals(TreebankNode node) {
		List<TreebankNode> tList = new ArrayList<TreebankNode>();

		if (node.getChildren().size() == 0) {
			tList.add(node);
			return tList;
		}

		TreebankNode[] children = Arrays.asList(node.getChildren().toArray()).toArray(
				new TreebankNode[node.getChildren().size()]);

		for (TreebankNode child : children) {
			tList.addAll(getTerminals(child));
		}
		return tList;
	}

	@Override
	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		super.initialize(ctx);

		ConfigurationParameterInitializer.initializeConfigurationParameters(this, ctx);
		
		try {
			MaxentModel buildModel = new SuffixSensitiveGISModelReader(new File(buildModelFile)).getModel();
			MaxentModel checkModel = new SuffixSensitiveGISModelReader(new File(checkModelFile)).getModel();
			
			ParserTagger tagger = new opennlp.tools.lang.english.ParserTagger(postagModelFile, postagDictionaryFile, caseSensitive);
			ParserChunker chunker = new ParserChunker(chunkModelFile);
			HeadRules hrules = new HeadRules(headRulesFile);

			this.parser = new Parser(buildModel, checkModel, tagger, chunker, hrules, beamSize, advancePercentage);
		}
		catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
	}

}
