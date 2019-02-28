// Generated from ./parser/shared/src/main/scala/jbok/solidity/grammar/Solidity.g4 by ANTLR 4.7.2
package jbok.solidity.grammar;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class SolidityParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, T__23=24, 
		T__24=25, T__25=26, T__26=27, T__27=28, T__28=29, T__29=30, T__30=31, 
		T__31=32, T__32=33, T__33=34, T__34=35, T__35=36, T__36=37, T__37=38, 
		T__38=39, T__39=40, T__40=41, T__41=42, T__42=43, T__43=44, T__44=45, 
		T__45=46, T__46=47, T__47=48, T__48=49, T__49=50, T__50=51, T__51=52, 
		T__52=53, T__53=54, T__54=55, T__55=56, T__56=57, T__57=58, T__58=59, 
		T__59=60, T__60=61, T__61=62, T__62=63, T__63=64, T__64=65, T__65=66, 
		T__66=67, T__67=68, T__68=69, T__69=70, T__70=71, T__71=72, T__72=73, 
		T__73=74, T__74=75, T__75=76, T__76=77, T__77=78, T__78=79, T__79=80, 
		T__80=81, T__81=82, T__82=83, T__83=84, T__84=85, T__85=86, T__86=87, 
		T__87=88, T__88=89, T__89=90, Int=91, Uint=92, Byte=93, Fixed=94, Ufixed=95, 
		VersionLiteral=96, BooleanLiteral=97, DecimalNumber=98, HexNumber=99, 
		NumberUnit=100, HexLiteral=101, ReservedKeyword=102, AnonymousKeyword=103, 
		BreakKeyword=104, ConstantKeyword=105, ContinueKeyword=106, ExternalKeyword=107, 
		IndexedKeyword=108, InternalKeyword=109, PayableKeyword=110, PrivateKeyword=111, 
		PublicKeyword=112, PureKeyword=113, ViewKeyword=114, Identifier=115, StringLiteral=116, 
		WS=117, COMMENT=118, LINE_COMMENT=119;
	public static final int
		RULE_sourceUnit = 0, RULE_pragmaDirective = 1, RULE_pragmaName = 2, RULE_pragmaValue = 3, 
		RULE_version = 4, RULE_versionOperator = 5, RULE_versionConstraint = 6, 
		RULE_importDeclaration = 7, RULE_importDirective = 8, RULE_contractDefinition = 9, 
		RULE_inheritanceSpecifier = 10, RULE_contractPart = 11, RULE_stateVariableDeclaration = 12, 
		RULE_variableModifiers = 13, RULE_usingForDeclaration = 14, RULE_structDefinition = 15, 
		RULE_constructorDefinition = 16, RULE_modifierDefinition = 17, RULE_modifierInvocation = 18, 
		RULE_functionDefinition = 19, RULE_returnParameters = 20, RULE_modifierElement = 21, 
		RULE_modifierList = 22, RULE_eventDefinition = 23, RULE_enumValue = 24, 
		RULE_enumDefinition = 25, RULE_parameterList = 26, RULE_parameter = 27, 
		RULE_parameterStatement = 28, RULE_eventParameterList = 29, RULE_eventParameter = 30, 
		RULE_functionTypeParameterList = 31, RULE_functionTypeParameter = 32, 
		RULE_variableDeclaration = 33, RULE_typeName = 34, RULE_typeNameParam = 35, 
		RULE_userDefinedTypeName = 36, RULE_mapping = 37, RULE_functionTypeName = 38, 
		RULE_storageLocation = 39, RULE_visibility = 40, RULE_stateMutability = 41, 
		RULE_block = 42, RULE_statement = 43, RULE_expressionStatement = 44, RULE_ifStatement = 45, 
		RULE_whileStatement = 46, RULE_simpleStatement = 47, RULE_forStatement = 48, 
		RULE_inlineAssemblyStatement = 49, RULE_doWhileStatement = 50, RULE_continueStatement = 51, 
		RULE_breakStatement = 52, RULE_returnStatement = 53, RULE_throwStatement = 54, 
		RULE_emitStatement = 55, RULE_variableDeclarationStatement = 56, RULE_variableDeclarationList = 57, 
		RULE_identifierList = 58, RULE_elementaryTypeName = 59, RULE_expression = 60, 
		RULE_constantExpression = 61, RULE_primaryExpression = 62, RULE_expressionList = 63, 
		RULE_nameValueList = 64, RULE_nameValue = 65, RULE_functionCallArguments = 66, 
		RULE_functionCall = 67, RULE_assemblyBlock = 68, RULE_assemblyItem = 69, 
		RULE_assemblyExpression = 70, RULE_assemblyCall = 71, RULE_assemblyLocalDefinition = 72, 
		RULE_assemblyAssignment = 73, RULE_assemblyIdentifierOrList = 74, RULE_assemblyIdentifierList = 75, 
		RULE_assemblyStackAssignment = 76, RULE_labelDefinition = 77, RULE_assemblySwitch = 78, 
		RULE_assemblyCase = 79, RULE_assemblyFunctionDefinition = 80, RULE_assemblyFunctionReturns = 81, 
		RULE_assemblyFor = 82, RULE_assemblyIf = 83, RULE_assemblyLiteral = 84, 
		RULE_subAssembly = 85, RULE_tupleExpression = 86, RULE_elementaryTypeNameExpression = 87, 
		RULE_numberLiteral = 88, RULE_numberLiteralNoUnit = 89, RULE_identifier = 90;
	private static String[] makeRuleNames() {
		return new String[] {
			"sourceUnit", "pragmaDirective", "pragmaName", "pragmaValue", "version", 
			"versionOperator", "versionConstraint", "importDeclaration", "importDirective", 
			"contractDefinition", "inheritanceSpecifier", "contractPart", "stateVariableDeclaration", 
			"variableModifiers", "usingForDeclaration", "structDefinition", "constructorDefinition", 
			"modifierDefinition", "modifierInvocation", "functionDefinition", "returnParameters", 
			"modifierElement", "modifierList", "eventDefinition", "enumValue", "enumDefinition", 
			"parameterList", "parameter", "parameterStatement", "eventParameterList", 
			"eventParameter", "functionTypeParameterList", "functionTypeParameter", 
			"variableDeclaration", "typeName", "typeNameParam", "userDefinedTypeName", 
			"mapping", "functionTypeName", "storageLocation", "visibility", "stateMutability", 
			"block", "statement", "expressionStatement", "ifStatement", "whileStatement", 
			"simpleStatement", "forStatement", "inlineAssemblyStatement", "doWhileStatement", 
			"continueStatement", "breakStatement", "returnStatement", "throwStatement", 
			"emitStatement", "variableDeclarationStatement", "variableDeclarationList", 
			"identifierList", "elementaryTypeName", "expression", "constantExpression", 
			"primaryExpression", "expressionList", "nameValueList", "nameValue", 
			"functionCallArguments", "functionCall", "assemblyBlock", "assemblyItem", 
			"assemblyExpression", "assemblyCall", "assemblyLocalDefinition", "assemblyAssignment", 
			"assemblyIdentifierOrList", "assemblyIdentifierList", "assemblyStackAssignment", 
			"labelDefinition", "assemblySwitch", "assemblyCase", "assemblyFunctionDefinition", 
			"assemblyFunctionReturns", "assemblyFor", "assemblyIf", "assemblyLiteral", 
			"subAssembly", "tupleExpression", "elementaryTypeNameExpression", "numberLiteral", 
			"numberLiteralNoUnit", "identifier"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'pragma'", "';'", "'^'", "'~'", "'>='", "'>'", "'<'", "'<='", 
			"'='", "'as'", "'import'", "'*'", "'from'", "'{'", "','", "'}'", "'contract'", 
			"'interface'", "'library'", "'is'", "'('", "')'", "'using'", "'for'", 
			"'struct'", "'constructor'", "'modifier'", "'function'", "'returns'", 
			"'event'", "'enum'", "'['", "']'", "'address'", "'.'", "'mapping'", "'=>'", 
			"'memory'", "'storage'", "'calldata'", "'if'", "'else'", "'while'", "'assembly'", 
			"'do'", "'return'", "'throw'", "'emit'", "'var'", "'bool'", "'string'", 
			"'byte'", "'++'", "'--'", "'new'", "'+'", "'-'", "'after'", "'delete'", 
			"'!'", "'**'", "'/'", "'%'", "'<<'", "'>>'", "'&'", "'|'", "'=='", "'!='", 
			"'&&'", "'||'", "'?'", "':'", "'|='", "'^='", "'&='", "'<<='", "'>>='", 
			"'+='", "'-='", "'*='", "'/='", "'%='", "'let'", "':='", "'=:'", "'switch'", 
			"'case'", "'default'", "'->'", null, null, null, null, null, null, null, 
			null, null, null, null, null, "'anonymous'", "'break'", "'constant'", 
			"'continue'", "'external'", "'indexed'", "'internal'", "'payable'", "'private'", 
			"'public'", "'pure'", "'view'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, "Int", "Uint", "Byte", "Fixed", 
			"Ufixed", "VersionLiteral", "BooleanLiteral", "DecimalNumber", "HexNumber", 
			"NumberUnit", "HexLiteral", "ReservedKeyword", "AnonymousKeyword", "BreakKeyword", 
			"ConstantKeyword", "ContinueKeyword", "ExternalKeyword", "IndexedKeyword", 
			"InternalKeyword", "PayableKeyword", "PrivateKeyword", "PublicKeyword", 
			"PureKeyword", "ViewKeyword", "Identifier", "StringLiteral", "WS", "COMMENT", 
			"LINE_COMMENT"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "Solidity.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public SolidityParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class SourceUnitContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(SolidityParser.EOF, 0); }
		public List<PragmaDirectiveContext> pragmaDirective() {
			return getRuleContexts(PragmaDirectiveContext.class);
		}
		public PragmaDirectiveContext pragmaDirective(int i) {
			return getRuleContext(PragmaDirectiveContext.class,i);
		}
		public List<ImportDirectiveContext> importDirective() {
			return getRuleContexts(ImportDirectiveContext.class);
		}
		public ImportDirectiveContext importDirective(int i) {
			return getRuleContext(ImportDirectiveContext.class,i);
		}
		public List<ContractDefinitionContext> contractDefinition() {
			return getRuleContexts(ContractDefinitionContext.class);
		}
		public ContractDefinitionContext contractDefinition(int i) {
			return getRuleContext(ContractDefinitionContext.class,i);
		}
		public SourceUnitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sourceUnit; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitSourceUnit(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SourceUnitContext sourceUnit() throws RecognitionException {
		SourceUnitContext _localctx = new SourceUnitContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_sourceUnit);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(187);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__10) | (1L << T__16) | (1L << T__17) | (1L << T__18))) != 0)) {
				{
				setState(185);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
					{
					setState(182);
					pragmaDirective();
					}
					break;
				case T__10:
					{
					setState(183);
					importDirective();
					}
					break;
				case T__16:
				case T__17:
				case T__18:
					{
					setState(184);
					contractDefinition();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(189);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(190);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PragmaDirectiveContext extends ParserRuleContext {
		public PragmaNameContext pragmaName() {
			return getRuleContext(PragmaNameContext.class,0);
		}
		public PragmaValueContext pragmaValue() {
			return getRuleContext(PragmaValueContext.class,0);
		}
		public PragmaDirectiveContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pragmaDirective; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitPragmaDirective(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PragmaDirectiveContext pragmaDirective() throws RecognitionException {
		PragmaDirectiveContext _localctx = new PragmaDirectiveContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_pragmaDirective);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(192);
			match(T__0);
			setState(193);
			pragmaName();
			setState(194);
			pragmaValue();
			setState(195);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PragmaNameContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public PragmaNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pragmaName; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitPragmaName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PragmaNameContext pragmaName() throws RecognitionException {
		PragmaNameContext _localctx = new PragmaNameContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_pragmaName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(197);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PragmaValueContext extends ParserRuleContext {
		public VersionContext version() {
			return getRuleContext(VersionContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public PragmaValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pragmaValue; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitPragmaValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PragmaValueContext pragmaValue() throws RecognitionException {
		PragmaValueContext _localctx = new PragmaValueContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_pragmaValue);
		try {
			setState(201);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(199);
				version();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(200);
				expression(0);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VersionContext extends ParserRuleContext {
		public List<VersionConstraintContext> versionConstraint() {
			return getRuleContexts(VersionConstraintContext.class);
		}
		public VersionConstraintContext versionConstraint(int i) {
			return getRuleContext(VersionConstraintContext.class,i);
		}
		public VersionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_version; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitVersion(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VersionContext version() throws RecognitionException {
		VersionContext _localctx = new VersionContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_version);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(203);
			versionConstraint();
			setState(205);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8))) != 0) || _la==VersionLiteral) {
				{
				setState(204);
				versionConstraint();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VersionOperatorContext extends ParserRuleContext {
		public VersionOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_versionOperator; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitVersionOperator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VersionOperatorContext versionOperator() throws RecognitionException {
		VersionOperatorContext _localctx = new VersionOperatorContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_versionOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(207);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VersionConstraintContext extends ParserRuleContext {
		public TerminalNode VersionLiteral() { return getToken(SolidityParser.VersionLiteral, 0); }
		public VersionOperatorContext versionOperator() {
			return getRuleContext(VersionOperatorContext.class,0);
		}
		public VersionConstraintContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_versionConstraint; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitVersionConstraint(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VersionConstraintContext versionConstraint() throws RecognitionException {
		VersionConstraintContext _localctx = new VersionConstraintContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_versionConstraint);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(210);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8))) != 0)) {
				{
				setState(209);
				versionOperator();
				}
			}

			setState(212);
			match(VersionLiteral);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ImportDeclarationContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public ImportDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitImportDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImportDeclarationContext importDeclaration() throws RecognitionException {
		ImportDeclarationContext _localctx = new ImportDeclarationContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_importDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(214);
			identifier();
			setState(217);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__9) {
				{
				setState(215);
				match(T__9);
				setState(216);
				identifier();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ImportDirectiveContext extends ParserRuleContext {
		public TerminalNode StringLiteral() { return getToken(SolidityParser.StringLiteral, 0); }
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public List<ImportDeclarationContext> importDeclaration() {
			return getRuleContexts(ImportDeclarationContext.class);
		}
		public ImportDeclarationContext importDeclaration(int i) {
			return getRuleContext(ImportDeclarationContext.class,i);
		}
		public ImportDirectiveContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importDirective; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitImportDirective(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImportDirectiveContext importDirective() throws RecognitionException {
		ImportDirectiveContext _localctx = new ImportDirectiveContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_importDirective);
		int _la;
		try {
			setState(253);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,10,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(219);
				match(T__10);
				setState(220);
				match(StringLiteral);
				setState(223);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__9) {
					{
					setState(221);
					match(T__9);
					setState(222);
					identifier();
					}
				}

				setState(225);
				match(T__1);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(226);
				match(T__10);
				setState(229);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__11:
					{
					setState(227);
					match(T__11);
					}
					break;
				case T__12:
				case T__39:
				case Identifier:
					{
					setState(228);
					identifier();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(233);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__9) {
					{
					setState(231);
					match(T__9);
					setState(232);
					identifier();
					}
				}

				setState(235);
				match(T__12);
				setState(236);
				match(StringLiteral);
				setState(237);
				match(T__1);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(238);
				match(T__10);
				setState(239);
				match(T__13);
				setState(240);
				importDeclaration();
				setState(245);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__14) {
					{
					{
					setState(241);
					match(T__14);
					setState(242);
					importDeclaration();
					}
					}
					setState(247);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(248);
				match(T__15);
				setState(249);
				match(T__12);
				setState(250);
				match(StringLiteral);
				setState(251);
				match(T__1);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ContractDefinitionContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public List<InheritanceSpecifierContext> inheritanceSpecifier() {
			return getRuleContexts(InheritanceSpecifierContext.class);
		}
		public InheritanceSpecifierContext inheritanceSpecifier(int i) {
			return getRuleContext(InheritanceSpecifierContext.class,i);
		}
		public List<ContractPartContext> contractPart() {
			return getRuleContexts(ContractPartContext.class);
		}
		public ContractPartContext contractPart(int i) {
			return getRuleContext(ContractPartContext.class,i);
		}
		public ContractDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_contractDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitContractDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ContractDefinitionContext contractDefinition() throws RecognitionException {
		ContractDefinitionContext _localctx = new ContractDefinitionContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_contractDefinition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(255);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__16) | (1L << T__17) | (1L << T__18))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(256);
			identifier();
			setState(266);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__19) {
				{
				setState(257);
				match(T__19);
				setState(258);
				inheritanceSpecifier();
				setState(263);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__14) {
					{
					{
					setState(259);
					match(T__14);
					setState(260);
					inheritanceSpecifier();
					}
					}
					setState(265);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(268);
			match(T__13);
			setState(272);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__22) | (1L << T__24) | (1L << T__25) | (1L << T__26) | (1L << T__27) | (1L << T__29) | (1L << T__30) | (1L << T__33) | (1L << T__35) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (Identifier - 91)))) != 0)) {
				{
				{
				setState(269);
				contractPart();
				}
				}
				setState(274);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(275);
			match(T__15);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class InheritanceSpecifierContext extends ParserRuleContext {
		public UserDefinedTypeNameContext userDefinedTypeName() {
			return getRuleContext(UserDefinedTypeNameContext.class,0);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public InheritanceSpecifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inheritanceSpecifier; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitInheritanceSpecifier(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InheritanceSpecifierContext inheritanceSpecifier() throws RecognitionException {
		InheritanceSpecifierContext _localctx = new InheritanceSpecifierContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_inheritanceSpecifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(277);
			userDefinedTypeName();
			setState(289);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__20) {
				{
				setState(278);
				match(T__20);
				setState(279);
				expression(0);
				setState(284);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__14) {
					{
					{
					setState(280);
					match(T__14);
					setState(281);
					expression(0);
					}
					}
					setState(286);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(287);
				match(T__21);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ContractPartContext extends ParserRuleContext {
		public ContractPartContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_contractPart; }
	 
		public ContractPartContext() { }
		public void copyFrom(ContractPartContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class StateVariablePartContext extends ContractPartContext {
		public StateVariableDeclarationContext stateVariableDeclaration() {
			return getRuleContext(StateVariableDeclarationContext.class,0);
		}
		public StateVariablePartContext(ContractPartContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitStateVariablePart(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class StructPartContext extends ContractPartContext {
		public StructDefinitionContext structDefinition() {
			return getRuleContext(StructDefinitionContext.class,0);
		}
		public StructPartContext(ContractPartContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitStructPart(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class EventPartContext extends ContractPartContext {
		public EventDefinitionContext eventDefinition() {
			return getRuleContext(EventDefinitionContext.class,0);
		}
		public EventPartContext(ContractPartContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitEventPart(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class UsingPartContext extends ContractPartContext {
		public UsingForDeclarationContext usingForDeclaration() {
			return getRuleContext(UsingForDeclarationContext.class,0);
		}
		public UsingPartContext(ContractPartContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitUsingPart(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ConstructorPartContext extends ContractPartContext {
		public ConstructorDefinitionContext constructorDefinition() {
			return getRuleContext(ConstructorDefinitionContext.class,0);
		}
		public ConstructorPartContext(ContractPartContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitConstructorPart(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class FunctionPartContext extends ContractPartContext {
		public FunctionDefinitionContext functionDefinition() {
			return getRuleContext(FunctionDefinitionContext.class,0);
		}
		public FunctionPartContext(ContractPartContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitFunctionPart(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ModifierPartContext extends ContractPartContext {
		public ModifierDefinitionContext modifierDefinition() {
			return getRuleContext(ModifierDefinitionContext.class,0);
		}
		public ModifierPartContext(ContractPartContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitModifierPart(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class EnumPartContext extends ContractPartContext {
		public EnumDefinitionContext enumDefinition() {
			return getRuleContext(EnumDefinitionContext.class,0);
		}
		public EnumPartContext(ContractPartContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitEnumPart(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ContractPartContext contractPart() throws RecognitionException {
		ContractPartContext _localctx = new ContractPartContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_contractPart);
		try {
			setState(299);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__12:
			case T__33:
			case T__35:
			case T__39:
			case T__48:
			case T__49:
			case T__50:
			case T__51:
			case Int:
			case Uint:
			case Byte:
			case Fixed:
			case Ufixed:
			case Identifier:
				_localctx = new StateVariablePartContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(291);
				stateVariableDeclaration();
				}
				break;
			case T__22:
				_localctx = new UsingPartContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(292);
				usingForDeclaration();
				}
				break;
			case T__24:
				_localctx = new StructPartContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(293);
				structDefinition();
				}
				break;
			case T__25:
				_localctx = new ConstructorPartContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(294);
				constructorDefinition();
				}
				break;
			case T__26:
				_localctx = new ModifierPartContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(295);
				modifierDefinition();
				}
				break;
			case T__27:
				_localctx = new FunctionPartContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(296);
				functionDefinition();
				}
				break;
			case T__29:
				_localctx = new EventPartContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(297);
				eventDefinition();
				}
				break;
			case T__30:
				_localctx = new EnumPartContext(_localctx);
				enterOuterAlt(_localctx, 8);
				{
				setState(298);
				enumDefinition();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StateVariableDeclarationContext extends ParserRuleContext {
		public TypeNameParamContext typeNameParam() {
			return getRuleContext(TypeNameParamContext.class,0);
		}
		public VariableModifiersContext variableModifiers() {
			return getRuleContext(VariableModifiersContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public StateVariableDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stateVariableDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitStateVariableDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StateVariableDeclarationContext stateVariableDeclaration() throws RecognitionException {
		StateVariableDeclarationContext _localctx = new StateVariableDeclarationContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_stateVariableDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(301);
			typeNameParam(0);
			setState(302);
			variableModifiers();
			setState(303);
			identifier();
			setState(306);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__8) {
				{
				setState(304);
				match(T__8);
				setState(305);
				expression(0);
				}
			}

			setState(308);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VariableModifiersContext extends ParserRuleContext {
		public TerminalNode ConstantKeyword() { return getToken(SolidityParser.ConstantKeyword, 0); }
		public TerminalNode PublicKeyword() { return getToken(SolidityParser.PublicKeyword, 0); }
		public TerminalNode InternalKeyword() { return getToken(SolidityParser.InternalKeyword, 0); }
		public TerminalNode PrivateKeyword() { return getToken(SolidityParser.PrivateKeyword, 0); }
		public VariableModifiersContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variableModifiers; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitVariableModifiers(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VariableModifiersContext variableModifiers() throws RecognitionException {
		VariableModifiersContext _localctx = new VariableModifiersContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_variableModifiers);
		int _la;
		try {
			setState(319);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(311);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 109)) & ~0x3f) == 0 && ((1L << (_la - 109)) & ((1L << (InternalKeyword - 109)) | (1L << (PrivateKeyword - 109)) | (1L << (PublicKeyword - 109)))) != 0)) {
					{
					setState(310);
					_la = _input.LA(1);
					if ( !(((((_la - 109)) & ~0x3f) == 0 && ((1L << (_la - 109)) & ((1L << (InternalKeyword - 109)) | (1L << (PrivateKeyword - 109)) | (1L << (PublicKeyword - 109)))) != 0)) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					}
				}

				setState(313);
				match(ConstantKeyword);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(315);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ConstantKeyword) {
					{
					setState(314);
					match(ConstantKeyword);
					}
				}

				setState(317);
				_la = _input.LA(1);
				if ( !(((((_la - 109)) & ~0x3f) == 0 && ((1L << (_la - 109)) & ((1L << (InternalKeyword - 109)) | (1L << (PrivateKeyword - 109)) | (1L << (PublicKeyword - 109)))) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class UsingForDeclarationContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public UsingForDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_usingForDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitUsingForDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UsingForDeclarationContext usingForDeclaration() throws RecognitionException {
		UsingForDeclarationContext _localctx = new UsingForDeclarationContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_usingForDeclaration);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(321);
			match(T__22);
			setState(322);
			identifier();
			setState(323);
			match(T__23);
			setState(326);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__11:
				{
				setState(324);
				match(T__11);
				}
				break;
			case T__12:
			case T__27:
			case T__33:
			case T__35:
			case T__39:
			case T__48:
			case T__49:
			case T__50:
			case T__51:
			case Int:
			case Uint:
			case Byte:
			case Fixed:
			case Ufixed:
			case Identifier:
				{
				setState(325);
				typeName(0);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(328);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StructDefinitionContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public List<VariableDeclarationContext> variableDeclaration() {
			return getRuleContexts(VariableDeclarationContext.class);
		}
		public VariableDeclarationContext variableDeclaration(int i) {
			return getRuleContext(VariableDeclarationContext.class,i);
		}
		public StructDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_structDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitStructDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StructDefinitionContext structDefinition() throws RecognitionException {
		StructDefinitionContext _localctx = new StructDefinitionContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_structDefinition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(330);
			match(T__24);
			setState(331);
			identifier();
			setState(332);
			match(T__13);
			setState(343);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__33) | (1L << T__35) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (Identifier - 91)))) != 0)) {
				{
				setState(333);
				variableDeclaration();
				setState(334);
				match(T__1);
				setState(340);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__33) | (1L << T__35) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (Identifier - 91)))) != 0)) {
					{
					{
					setState(335);
					variableDeclaration();
					setState(336);
					match(T__1);
					}
					}
					setState(342);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(345);
			match(T__15);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ConstructorDefinitionContext extends ParserRuleContext {
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public ModifierListContext modifierList() {
			return getRuleContext(ModifierListContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ConstructorDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constructorDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitConstructorDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConstructorDefinitionContext constructorDefinition() throws RecognitionException {
		ConstructorDefinitionContext _localctx = new ConstructorDefinitionContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_constructorDefinition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(347);
			match(T__25);
			setState(348);
			parameterList();
			setState(349);
			modifierList();
			setState(350);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ModifierDefinitionContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public ModifierDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_modifierDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitModifierDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ModifierDefinitionContext modifierDefinition() throws RecognitionException {
		ModifierDefinitionContext _localctx = new ModifierDefinitionContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_modifierDefinition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(352);
			match(T__26);
			setState(353);
			identifier();
			setState(355);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__20) {
				{
				setState(354);
				parameterList();
				}
			}

			setState(357);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ModifierInvocationContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ExpressionListContext expressionList() {
			return getRuleContext(ExpressionListContext.class,0);
		}
		public ModifierInvocationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_modifierInvocation; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitModifierInvocation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ModifierInvocationContext modifierInvocation() throws RecognitionException {
		ModifierInvocationContext _localctx = new ModifierInvocationContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_modifierInvocation);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(359);
			identifier();
			setState(365);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__20) {
				{
				setState(360);
				match(T__20);
				setState(362);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__20) | (1L << T__31) | (1L << T__33) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
					{
					setState(361);
					expressionList();
					}
				}

				setState(364);
				match(T__21);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionDefinitionContext extends ParserRuleContext {
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public ModifierListContext modifierList() {
			return getRuleContext(ModifierListContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ReturnParametersContext returnParameters() {
			return getRuleContext(ReturnParametersContext.class,0);
		}
		public FunctionDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitFunctionDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionDefinitionContext functionDefinition() throws RecognitionException {
		FunctionDefinitionContext _localctx = new FunctionDefinitionContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_functionDefinition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(367);
			match(T__27);
			setState(369);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__12 || _la==T__39 || _la==Identifier) {
				{
				setState(368);
				identifier();
				}
			}

			setState(371);
			parameterList();
			setState(372);
			modifierList();
			setState(374);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__28) {
				{
				setState(373);
				returnParameters();
				}
			}

			setState(378);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__1:
				{
				setState(376);
				match(T__1);
				}
				break;
			case T__13:
				{
				setState(377);
				block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ReturnParametersContext extends ParserRuleContext {
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public ReturnParametersContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_returnParameters; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitReturnParameters(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReturnParametersContext returnParameters() throws RecognitionException {
		ReturnParametersContext _localctx = new ReturnParametersContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_returnParameters);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(380);
			match(T__28);
			setState(381);
			parameterList();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ModifierElementContext extends ParserRuleContext {
		public ModifierInvocationContext modifierInvocation() {
			return getRuleContext(ModifierInvocationContext.class,0);
		}
		public StateMutabilityContext stateMutability() {
			return getRuleContext(StateMutabilityContext.class,0);
		}
		public VisibilityContext visibility() {
			return getRuleContext(VisibilityContext.class,0);
		}
		public ModifierElementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_modifierElement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitModifierElement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ModifierElementContext modifierElement() throws RecognitionException {
		ModifierElementContext _localctx = new ModifierElementContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_modifierElement);
		try {
			setState(386);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__12:
			case T__39:
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(383);
				modifierInvocation();
				}
				break;
			case ConstantKeyword:
			case PayableKeyword:
			case PureKeyword:
			case ViewKeyword:
				enterOuterAlt(_localctx, 2);
				{
				setState(384);
				stateMutability();
				}
				break;
			case ExternalKeyword:
			case InternalKeyword:
			case PrivateKeyword:
			case PublicKeyword:
				enterOuterAlt(_localctx, 3);
				{
				setState(385);
				visibility();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ModifierListContext extends ParserRuleContext {
		public List<ModifierElementContext> modifierElement() {
			return getRuleContexts(ModifierElementContext.class);
		}
		public ModifierElementContext modifierElement(int i) {
			return getRuleContext(ModifierElementContext.class,i);
		}
		public ModifierListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_modifierList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitModifierList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ModifierListContext modifierList() throws RecognitionException {
		ModifierListContext _localctx = new ModifierListContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_modifierList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(391);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__12 || _la==T__39 || ((((_la - 105)) & ~0x3f) == 0 && ((1L << (_la - 105)) & ((1L << (ConstantKeyword - 105)) | (1L << (ExternalKeyword - 105)) | (1L << (InternalKeyword - 105)) | (1L << (PayableKeyword - 105)) | (1L << (PrivateKeyword - 105)) | (1L << (PublicKeyword - 105)) | (1L << (PureKeyword - 105)) | (1L << (ViewKeyword - 105)) | (1L << (Identifier - 105)))) != 0)) {
				{
				{
				setState(388);
				modifierElement();
				}
				}
				setState(393);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class EventDefinitionContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public EventParameterListContext eventParameterList() {
			return getRuleContext(EventParameterListContext.class,0);
		}
		public TerminalNode AnonymousKeyword() { return getToken(SolidityParser.AnonymousKeyword, 0); }
		public EventDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_eventDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitEventDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EventDefinitionContext eventDefinition() throws RecognitionException {
		EventDefinitionContext _localctx = new EventDefinitionContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_eventDefinition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(394);
			match(T__29);
			setState(395);
			identifier();
			setState(396);
			eventParameterList();
			setState(398);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AnonymousKeyword) {
				{
				setState(397);
				match(AnonymousKeyword);
				}
			}

			setState(400);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class EnumValueContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public EnumValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumValue; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitEnumValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumValueContext enumValue() throws RecognitionException {
		EnumValueContext _localctx = new EnumValueContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_enumValue);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(402);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class EnumDefinitionContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public List<EnumValueContext> enumValue() {
			return getRuleContexts(EnumValueContext.class);
		}
		public EnumValueContext enumValue(int i) {
			return getRuleContext(EnumValueContext.class,i);
		}
		public EnumDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitEnumDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumDefinitionContext enumDefinition() throws RecognitionException {
		EnumDefinitionContext _localctx = new EnumDefinitionContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_enumDefinition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(404);
			match(T__30);
			setState(405);
			identifier();
			setState(406);
			match(T__13);
			setState(408);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__12 || _la==T__39 || _la==Identifier) {
				{
				setState(407);
				enumValue();
				}
			}

			setState(414);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(410);
				match(T__14);
				setState(411);
				enumValue();
				}
				}
				setState(416);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(417);
			match(T__15);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ParameterListContext extends ParserRuleContext {
		public List<ParameterStatementContext> parameterStatement() {
			return getRuleContexts(ParameterStatementContext.class);
		}
		public ParameterStatementContext parameterStatement(int i) {
			return getRuleContext(ParameterStatementContext.class,i);
		}
		public ParameterListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitParameterList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterListContext parameterList() throws RecognitionException {
		ParameterListContext _localctx = new ParameterListContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_parameterList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(419);
			match(T__20);
			setState(428);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__33) | (1L << T__35) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (Identifier - 91)))) != 0)) {
				{
				setState(420);
				parameterStatement();
				setState(425);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__14) {
					{
					{
					setState(421);
					match(T__14);
					setState(422);
					parameterStatement();
					}
					}
					setState(427);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(430);
			match(T__21);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ParameterContext extends ParserRuleContext {
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public StorageLocationContext storageLocation() {
			return getRuleContext(StorageLocationContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ParameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameter; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitParameter(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterContext parameter() throws RecognitionException {
		ParameterContext _localctx = new ParameterContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_parameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(432);
			typeName(0);
			setState(434);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,37,_ctx) ) {
			case 1:
				{
				setState(433);
				storageLocation();
				}
				break;
			}
			setState(437);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__12 || _la==T__39 || _la==Identifier) {
				{
				setState(436);
				identifier();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ParameterStatementContext extends ParserRuleContext {
		public TypeNameParamContext typeNameParam() {
			return getRuleContext(TypeNameParamContext.class,0);
		}
		public StorageLocationContext storageLocation() {
			return getRuleContext(StorageLocationContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ParameterStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitParameterStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterStatementContext parameterStatement() throws RecognitionException {
		ParameterStatementContext _localctx = new ParameterStatementContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_parameterStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(439);
			typeNameParam(0);
			setState(441);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,39,_ctx) ) {
			case 1:
				{
				setState(440);
				storageLocation();
				}
				break;
			}
			setState(444);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__12 || _la==T__39 || _la==Identifier) {
				{
				setState(443);
				identifier();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class EventParameterListContext extends ParserRuleContext {
		public List<EventParameterContext> eventParameter() {
			return getRuleContexts(EventParameterContext.class);
		}
		public EventParameterContext eventParameter(int i) {
			return getRuleContext(EventParameterContext.class,i);
		}
		public EventParameterListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_eventParameterList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitEventParameterList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EventParameterListContext eventParameterList() throws RecognitionException {
		EventParameterListContext _localctx = new EventParameterListContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_eventParameterList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(446);
			match(T__20);
			setState(455);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__27) | (1L << T__33) | (1L << T__35) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (Identifier - 91)))) != 0)) {
				{
				setState(447);
				eventParameter();
				setState(452);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__14) {
					{
					{
					setState(448);
					match(T__14);
					setState(449);
					eventParameter();
					}
					}
					setState(454);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(457);
			match(T__21);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class EventParameterContext extends ParserRuleContext {
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public TerminalNode IndexedKeyword() { return getToken(SolidityParser.IndexedKeyword, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public EventParameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_eventParameter; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitEventParameter(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EventParameterContext eventParameter() throws RecognitionException {
		EventParameterContext _localctx = new EventParameterContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_eventParameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(459);
			typeName(0);
			setState(461);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IndexedKeyword) {
				{
				setState(460);
				match(IndexedKeyword);
				}
			}

			setState(464);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__12 || _la==T__39 || _la==Identifier) {
				{
				setState(463);
				identifier();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionTypeParameterListContext extends ParserRuleContext {
		public List<FunctionTypeParameterContext> functionTypeParameter() {
			return getRuleContexts(FunctionTypeParameterContext.class);
		}
		public FunctionTypeParameterContext functionTypeParameter(int i) {
			return getRuleContext(FunctionTypeParameterContext.class,i);
		}
		public FunctionTypeParameterListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionTypeParameterList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitFunctionTypeParameterList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionTypeParameterListContext functionTypeParameterList() throws RecognitionException {
		FunctionTypeParameterListContext _localctx = new FunctionTypeParameterListContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_functionTypeParameterList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(466);
			match(T__20);
			setState(475);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__27) | (1L << T__33) | (1L << T__35) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (Identifier - 91)))) != 0)) {
				{
				setState(467);
				functionTypeParameter();
				setState(472);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__14) {
					{
					{
					setState(468);
					match(T__14);
					setState(469);
					functionTypeParameter();
					}
					}
					setState(474);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(477);
			match(T__21);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionTypeParameterContext extends ParserRuleContext {
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public StorageLocationContext storageLocation() {
			return getRuleContext(StorageLocationContext.class,0);
		}
		public FunctionTypeParameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionTypeParameter; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitFunctionTypeParameter(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionTypeParameterContext functionTypeParameter() throws RecognitionException {
		FunctionTypeParameterContext _localctx = new FunctionTypeParameterContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_functionTypeParameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(479);
			typeName(0);
			setState(481);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__37) | (1L << T__38) | (1L << T__39))) != 0)) {
				{
				setState(480);
				storageLocation();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VariableDeclarationContext extends ParserRuleContext {
		public TypeNameParamContext typeNameParam() {
			return getRuleContext(TypeNameParamContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public StorageLocationContext storageLocation() {
			return getRuleContext(StorageLocationContext.class,0);
		}
		public VariableDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variableDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitVariableDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VariableDeclarationContext variableDeclaration() throws RecognitionException {
		VariableDeclarationContext _localctx = new VariableDeclarationContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_variableDeclaration);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(483);
			typeNameParam(0);
			setState(485);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,48,_ctx) ) {
			case 1:
				{
				setState(484);
				storageLocation();
				}
				break;
			}
			setState(487);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TypeNameContext extends ParserRuleContext {
		public ElementaryTypeNameContext elementaryTypeName() {
			return getRuleContext(ElementaryTypeNameContext.class,0);
		}
		public UserDefinedTypeNameContext userDefinedTypeName() {
			return getRuleContext(UserDefinedTypeNameContext.class,0);
		}
		public MappingContext mapping() {
			return getRuleContext(MappingContext.class,0);
		}
		public FunctionTypeNameContext functionTypeName() {
			return getRuleContext(FunctionTypeNameContext.class,0);
		}
		public TerminalNode PayableKeyword() { return getToken(SolidityParser.PayableKeyword, 0); }
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TypeNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeName; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitTypeName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeNameContext typeName() throws RecognitionException {
		return typeName(0);
	}

	private TypeNameContext typeName(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		TypeNameContext _localctx = new TypeNameContext(_ctx, _parentState);
		TypeNameContext _prevctx = _localctx;
		int _startState = 68;
		enterRecursionRule(_localctx, 68, RULE_typeName, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(496);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,49,_ctx) ) {
			case 1:
				{
				setState(490);
				elementaryTypeName();
				}
				break;
			case 2:
				{
				setState(491);
				userDefinedTypeName();
				}
				break;
			case 3:
				{
				setState(492);
				mapping();
				}
				break;
			case 4:
				{
				setState(493);
				functionTypeName();
				}
				break;
			case 5:
				{
				setState(494);
				match(T__33);
				setState(495);
				match(PayableKeyword);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(506);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,51,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new TypeNameContext(_parentctx, _parentState);
					pushNewRecursionContext(_localctx, _startState, RULE_typeName);
					setState(498);
					if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
					setState(499);
					match(T__31);
					setState(501);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__20) | (1L << T__31) | (1L << T__33) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
						{
						setState(500);
						expression(0);
						}
					}

					setState(503);
					match(T__32);
					}
					} 
				}
				setState(508);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,51,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class TypeNameParamContext extends ParserRuleContext {
		public TypeNameParamContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeNameParam; }
	 
		public TypeNameParamContext() { }
		public void copyFrom(TypeNameParamContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class MappingTypeContext extends TypeNameParamContext {
		public MappingContext mapping() {
			return getRuleContext(MappingContext.class,0);
		}
		public MappingTypeContext(TypeNameParamContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitMappingType(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ArrayTypeContext extends TypeNameParamContext {
		public TypeNameParamContext typeNameParam() {
			return getRuleContext(TypeNameParamContext.class,0);
		}
		public ConstantExpressionContext constantExpression() {
			return getRuleContext(ConstantExpressionContext.class,0);
		}
		public ArrayTypeContext(TypeNameParamContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitArrayType(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class UserDefinedTypeContext extends TypeNameParamContext {
		public UserDefinedTypeNameContext userDefinedTypeName() {
			return getRuleContext(UserDefinedTypeNameContext.class,0);
		}
		public UserDefinedTypeContext(TypeNameParamContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitUserDefinedType(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class PrimaryTypeContext extends TypeNameParamContext {
		public ElementaryTypeNameContext elementaryTypeName() {
			return getRuleContext(ElementaryTypeNameContext.class,0);
		}
		public PrimaryTypeContext(TypeNameParamContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitPrimaryType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeNameParamContext typeNameParam() throws RecognitionException {
		return typeNameParam(0);
	}

	private TypeNameParamContext typeNameParam(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		TypeNameParamContext _localctx = new TypeNameParamContext(_ctx, _parentState);
		TypeNameParamContext _prevctx = _localctx;
		int _startState = 70;
		enterRecursionRule(_localctx, 70, RULE_typeNameParam, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(513);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__33:
			case T__48:
			case T__49:
			case T__50:
			case T__51:
			case Int:
			case Uint:
			case Byte:
			case Fixed:
			case Ufixed:
				{
				_localctx = new PrimaryTypeContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(510);
				elementaryTypeName();
				}
				break;
			case T__12:
			case T__39:
			case Identifier:
				{
				_localctx = new UserDefinedTypeContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(511);
				userDefinedTypeName();
				}
				break;
			case T__35:
				{
				_localctx = new MappingTypeContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(512);
				mapping();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(523);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,54,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new ArrayTypeContext(new TypeNameParamContext(_parentctx, _parentState));
					pushNewRecursionContext(_localctx, _startState, RULE_typeNameParam);
					setState(515);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(516);
					match(T__31);
					setState(518);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__20) | (1L << T__55) | (1L << T__56))) != 0) || _la==DecimalNumber || _la==HexNumber) {
						{
						setState(517);
						constantExpression(0);
						}
					}

					setState(520);
					match(T__32);
					}
					} 
				}
				setState(525);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,54,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class UserDefinedTypeNameContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public UserDefinedTypeNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_userDefinedTypeName; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitUserDefinedTypeName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UserDefinedTypeNameContext userDefinedTypeName() throws RecognitionException {
		UserDefinedTypeNameContext _localctx = new UserDefinedTypeNameContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_userDefinedTypeName);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(526);
			identifier();
			setState(531);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(527);
					match(T__34);
					setState(528);
					identifier();
					}
					} 
				}
				setState(533);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class MappingContext extends ParserRuleContext {
		public ElementaryTypeNameContext elementaryTypeName() {
			return getRuleContext(ElementaryTypeNameContext.class,0);
		}
		public TypeNameParamContext typeNameParam() {
			return getRuleContext(TypeNameParamContext.class,0);
		}
		public MappingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mapping; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitMapping(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MappingContext mapping() throws RecognitionException {
		MappingContext _localctx = new MappingContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_mapping);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(534);
			match(T__35);
			setState(535);
			match(T__20);
			setState(536);
			elementaryTypeName();
			setState(537);
			match(T__36);
			setState(538);
			typeNameParam(0);
			setState(539);
			match(T__21);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionTypeNameContext extends ParserRuleContext {
		public List<FunctionTypeParameterListContext> functionTypeParameterList() {
			return getRuleContexts(FunctionTypeParameterListContext.class);
		}
		public FunctionTypeParameterListContext functionTypeParameterList(int i) {
			return getRuleContext(FunctionTypeParameterListContext.class,i);
		}
		public List<TerminalNode> InternalKeyword() { return getTokens(SolidityParser.InternalKeyword); }
		public TerminalNode InternalKeyword(int i) {
			return getToken(SolidityParser.InternalKeyword, i);
		}
		public List<TerminalNode> ExternalKeyword() { return getTokens(SolidityParser.ExternalKeyword); }
		public TerminalNode ExternalKeyword(int i) {
			return getToken(SolidityParser.ExternalKeyword, i);
		}
		public List<StateMutabilityContext> stateMutability() {
			return getRuleContexts(StateMutabilityContext.class);
		}
		public StateMutabilityContext stateMutability(int i) {
			return getRuleContext(StateMutabilityContext.class,i);
		}
		public FunctionTypeNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionTypeName; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitFunctionTypeName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionTypeNameContext functionTypeName() throws RecognitionException {
		FunctionTypeNameContext _localctx = new FunctionTypeNameContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_functionTypeName);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(541);
			match(T__27);
			setState(542);
			functionTypeParameterList();
			setState(548);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,57,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					setState(546);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case InternalKeyword:
						{
						setState(543);
						match(InternalKeyword);
						}
						break;
					case ExternalKeyword:
						{
						setState(544);
						match(ExternalKeyword);
						}
						break;
					case ConstantKeyword:
					case PayableKeyword:
					case PureKeyword:
					case ViewKeyword:
						{
						setState(545);
						stateMutability();
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					} 
				}
				setState(550);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,57,_ctx);
			}
			setState(553);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,58,_ctx) ) {
			case 1:
				{
				setState(551);
				match(T__28);
				setState(552);
				functionTypeParameterList();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StorageLocationContext extends ParserRuleContext {
		public StorageLocationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_storageLocation; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitStorageLocation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StorageLocationContext storageLocation() throws RecognitionException {
		StorageLocationContext _localctx = new StorageLocationContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_storageLocation);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(555);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__37) | (1L << T__38) | (1L << T__39))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VisibilityContext extends ParserRuleContext {
		public TerminalNode PublicKeyword() { return getToken(SolidityParser.PublicKeyword, 0); }
		public TerminalNode PrivateKeyword() { return getToken(SolidityParser.PrivateKeyword, 0); }
		public TerminalNode ExternalKeyword() { return getToken(SolidityParser.ExternalKeyword, 0); }
		public TerminalNode InternalKeyword() { return getToken(SolidityParser.InternalKeyword, 0); }
		public VisibilityContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_visibility; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitVisibility(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VisibilityContext visibility() throws RecognitionException {
		VisibilityContext _localctx = new VisibilityContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_visibility);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(557);
			_la = _input.LA(1);
			if ( !(((((_la - 107)) & ~0x3f) == 0 && ((1L << (_la - 107)) & ((1L << (ExternalKeyword - 107)) | (1L << (InternalKeyword - 107)) | (1L << (PrivateKeyword - 107)) | (1L << (PublicKeyword - 107)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StateMutabilityContext extends ParserRuleContext {
		public TerminalNode PureKeyword() { return getToken(SolidityParser.PureKeyword, 0); }
		public TerminalNode ConstantKeyword() { return getToken(SolidityParser.ConstantKeyword, 0); }
		public TerminalNode ViewKeyword() { return getToken(SolidityParser.ViewKeyword, 0); }
		public TerminalNode PayableKeyword() { return getToken(SolidityParser.PayableKeyword, 0); }
		public StateMutabilityContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stateMutability; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitStateMutability(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StateMutabilityContext stateMutability() throws RecognitionException {
		StateMutabilityContext _localctx = new StateMutabilityContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_stateMutability);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(559);
			_la = _input.LA(1);
			if ( !(((((_la - 105)) & ~0x3f) == 0 && ((1L << (_la - 105)) & ((1L << (ConstantKeyword - 105)) | (1L << (PayableKeyword - 105)) | (1L << (PureKeyword - 105)) | (1L << (ViewKeyword - 105)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BlockContext extends ParserRuleContext {
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(561);
			match(T__13);
			setState(565);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__13) | (1L << T__20) | (1L << T__23) | (1L << T__31) | (1L << T__33) | (1L << T__35) | (1L << T__39) | (1L << T__40) | (1L << T__42) | (1L << T__43) | (1L << T__44) | (1L << T__45) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (BreakKeyword - 91)) | (1L << (ContinueKeyword - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
				{
				{
				setState(562);
				statement();
				}
				}
				setState(567);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(568);
			match(T__15);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StatementContext extends ParserRuleContext {
		public IfStatementContext ifStatement() {
			return getRuleContext(IfStatementContext.class,0);
		}
		public WhileStatementContext whileStatement() {
			return getRuleContext(WhileStatementContext.class,0);
		}
		public ForStatementContext forStatement() {
			return getRuleContext(ForStatementContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public InlineAssemblyStatementContext inlineAssemblyStatement() {
			return getRuleContext(InlineAssemblyStatementContext.class,0);
		}
		public DoWhileStatementContext doWhileStatement() {
			return getRuleContext(DoWhileStatementContext.class,0);
		}
		public ContinueStatementContext continueStatement() {
			return getRuleContext(ContinueStatementContext.class,0);
		}
		public BreakStatementContext breakStatement() {
			return getRuleContext(BreakStatementContext.class,0);
		}
		public ReturnStatementContext returnStatement() {
			return getRuleContext(ReturnStatementContext.class,0);
		}
		public ThrowStatementContext throwStatement() {
			return getRuleContext(ThrowStatementContext.class,0);
		}
		public EmitStatementContext emitStatement() {
			return getRuleContext(EmitStatementContext.class,0);
		}
		public SimpleStatementContext simpleStatement() {
			return getRuleContext(SimpleStatementContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_statement);
		try {
			setState(582);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__40:
				enterOuterAlt(_localctx, 1);
				{
				setState(570);
				ifStatement();
				}
				break;
			case T__42:
				enterOuterAlt(_localctx, 2);
				{
				setState(571);
				whileStatement();
				}
				break;
			case T__23:
				enterOuterAlt(_localctx, 3);
				{
				setState(572);
				forStatement();
				}
				break;
			case T__13:
				enterOuterAlt(_localctx, 4);
				{
				setState(573);
				block();
				}
				break;
			case T__43:
				enterOuterAlt(_localctx, 5);
				{
				setState(574);
				inlineAssemblyStatement();
				}
				break;
			case T__44:
				enterOuterAlt(_localctx, 6);
				{
				setState(575);
				doWhileStatement();
				}
				break;
			case ContinueKeyword:
				enterOuterAlt(_localctx, 7);
				{
				setState(576);
				continueStatement();
				}
				break;
			case BreakKeyword:
				enterOuterAlt(_localctx, 8);
				{
				setState(577);
				breakStatement();
				}
				break;
			case T__45:
				enterOuterAlt(_localctx, 9);
				{
				setState(578);
				returnStatement();
				}
				break;
			case T__46:
				enterOuterAlt(_localctx, 10);
				{
				setState(579);
				throwStatement();
				}
				break;
			case T__47:
				enterOuterAlt(_localctx, 11);
				{
				setState(580);
				emitStatement();
				}
				break;
			case T__3:
			case T__12:
			case T__20:
			case T__31:
			case T__33:
			case T__35:
			case T__39:
			case T__48:
			case T__49:
			case T__50:
			case T__51:
			case T__52:
			case T__53:
			case T__54:
			case T__55:
			case T__56:
			case T__57:
			case T__58:
			case T__59:
			case Int:
			case Uint:
			case Byte:
			case Fixed:
			case Ufixed:
			case BooleanLiteral:
			case DecimalNumber:
			case HexNumber:
			case HexLiteral:
			case Identifier:
			case StringLiteral:
				enterOuterAlt(_localctx, 12);
				{
				setState(581);
				simpleStatement();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionStatementContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ExpressionStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expressionStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitExpressionStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionStatementContext expressionStatement() throws RecognitionException {
		ExpressionStatementContext _localctx = new ExpressionStatementContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_expressionStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(584);
			expression(0);
			setState(585);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IfStatementContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public IfStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ifStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitIfStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IfStatementContext ifStatement() throws RecognitionException {
		IfStatementContext _localctx = new IfStatementContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_ifStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(587);
			match(T__40);
			setState(588);
			match(T__20);
			setState(589);
			expression(0);
			setState(590);
			match(T__21);
			setState(591);
			statement();
			setState(594);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,61,_ctx) ) {
			case 1:
				{
				setState(592);
				match(T__41);
				setState(593);
				statement();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class WhileStatementContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public WhileStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_whileStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitWhileStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final WhileStatementContext whileStatement() throws RecognitionException {
		WhileStatementContext _localctx = new WhileStatementContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_whileStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(596);
			match(T__42);
			setState(597);
			match(T__20);
			setState(598);
			expression(0);
			setState(599);
			match(T__21);
			setState(600);
			statement();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SimpleStatementContext extends ParserRuleContext {
		public VariableDeclarationStatementContext variableDeclarationStatement() {
			return getRuleContext(VariableDeclarationStatementContext.class,0);
		}
		public ExpressionStatementContext expressionStatement() {
			return getRuleContext(ExpressionStatementContext.class,0);
		}
		public SimpleStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simpleStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitSimpleStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SimpleStatementContext simpleStatement() throws RecognitionException {
		SimpleStatementContext _localctx = new SimpleStatementContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_simpleStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(604);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,62,_ctx) ) {
			case 1:
				{
				setState(602);
				variableDeclarationStatement();
				}
				break;
			case 2:
				{
				setState(603);
				expressionStatement();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ForStatementContext extends ParserRuleContext {
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public SimpleStatementContext simpleStatement() {
			return getRuleContext(SimpleStatementContext.class,0);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ForStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitForStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ForStatementContext forStatement() throws RecognitionException {
		ForStatementContext _localctx = new ForStatementContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_forStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(606);
			match(T__23);
			setState(607);
			match(T__20);
			setState(610);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__3:
			case T__12:
			case T__20:
			case T__31:
			case T__33:
			case T__35:
			case T__39:
			case T__48:
			case T__49:
			case T__50:
			case T__51:
			case T__52:
			case T__53:
			case T__54:
			case T__55:
			case T__56:
			case T__57:
			case T__58:
			case T__59:
			case Int:
			case Uint:
			case Byte:
			case Fixed:
			case Ufixed:
			case BooleanLiteral:
			case DecimalNumber:
			case HexNumber:
			case HexLiteral:
			case Identifier:
			case StringLiteral:
				{
				setState(608);
				simpleStatement();
				}
				break;
			case T__1:
				{
				setState(609);
				match(T__1);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(613);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__20) | (1L << T__31) | (1L << T__33) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
				{
				setState(612);
				expression(0);
				}
			}

			setState(615);
			match(T__1);
			setState(617);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__20) | (1L << T__31) | (1L << T__33) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
				{
				setState(616);
				expression(0);
				}
			}

			setState(619);
			match(T__21);
			setState(620);
			statement();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class InlineAssemblyStatementContext extends ParserRuleContext {
		public AssemblyBlockContext assemblyBlock() {
			return getRuleContext(AssemblyBlockContext.class,0);
		}
		public TerminalNode StringLiteral() { return getToken(SolidityParser.StringLiteral, 0); }
		public InlineAssemblyStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inlineAssemblyStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitInlineAssemblyStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InlineAssemblyStatementContext inlineAssemblyStatement() throws RecognitionException {
		InlineAssemblyStatementContext _localctx = new InlineAssemblyStatementContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_inlineAssemblyStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(622);
			match(T__43);
			setState(624);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringLiteral) {
				{
				setState(623);
				match(StringLiteral);
				}
			}

			setState(626);
			assemblyBlock();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class DoWhileStatementContext extends ParserRuleContext {
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public DoWhileStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_doWhileStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitDoWhileStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DoWhileStatementContext doWhileStatement() throws RecognitionException {
		DoWhileStatementContext _localctx = new DoWhileStatementContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_doWhileStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(628);
			match(T__44);
			setState(629);
			statement();
			setState(630);
			match(T__42);
			setState(631);
			match(T__20);
			setState(632);
			expression(0);
			setState(633);
			match(T__21);
			setState(634);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ContinueStatementContext extends ParserRuleContext {
		public TerminalNode ContinueKeyword() { return getToken(SolidityParser.ContinueKeyword, 0); }
		public ContinueStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_continueStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitContinueStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ContinueStatementContext continueStatement() throws RecognitionException {
		ContinueStatementContext _localctx = new ContinueStatementContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_continueStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(636);
			match(ContinueKeyword);
			setState(637);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BreakStatementContext extends ParserRuleContext {
		public TerminalNode BreakKeyword() { return getToken(SolidityParser.BreakKeyword, 0); }
		public BreakStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_breakStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitBreakStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BreakStatementContext breakStatement() throws RecognitionException {
		BreakStatementContext _localctx = new BreakStatementContext(_ctx, getState());
		enterRule(_localctx, 104, RULE_breakStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(639);
			match(BreakKeyword);
			setState(640);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ReturnStatementContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ReturnStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_returnStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitReturnStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReturnStatementContext returnStatement() throws RecognitionException {
		ReturnStatementContext _localctx = new ReturnStatementContext(_ctx, getState());
		enterRule(_localctx, 106, RULE_returnStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(642);
			match(T__45);
			setState(644);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__20) | (1L << T__31) | (1L << T__33) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
				{
				setState(643);
				expression(0);
				}
			}

			setState(646);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ThrowStatementContext extends ParserRuleContext {
		public ThrowStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_throwStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitThrowStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ThrowStatementContext throwStatement() throws RecognitionException {
		ThrowStatementContext _localctx = new ThrowStatementContext(_ctx, getState());
		enterRule(_localctx, 108, RULE_throwStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(648);
			match(T__46);
			setState(649);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class EmitStatementContext extends ParserRuleContext {
		public FunctionCallContext functionCall() {
			return getRuleContext(FunctionCallContext.class,0);
		}
		public EmitStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_emitStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitEmitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EmitStatementContext emitStatement() throws RecognitionException {
		EmitStatementContext _localctx = new EmitStatementContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_emitStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(651);
			match(T__47);
			setState(652);
			functionCall();
			setState(653);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VariableDeclarationStatementContext extends ParserRuleContext {
		public IdentifierListContext identifierList() {
			return getRuleContext(IdentifierListContext.class,0);
		}
		public VariableDeclarationContext variableDeclaration() {
			return getRuleContext(VariableDeclarationContext.class,0);
		}
		public VariableDeclarationListContext variableDeclarationList() {
			return getRuleContext(VariableDeclarationListContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public VariableDeclarationStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variableDeclarationStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitVariableDeclarationStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VariableDeclarationStatementContext variableDeclarationStatement() throws RecognitionException {
		VariableDeclarationStatementContext _localctx = new VariableDeclarationStatementContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_variableDeclarationStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(662);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,68,_ctx) ) {
			case 1:
				{
				setState(655);
				match(T__48);
				setState(656);
				identifierList();
				}
				break;
			case 2:
				{
				setState(657);
				variableDeclaration();
				}
				break;
			case 3:
				{
				setState(658);
				match(T__20);
				setState(659);
				variableDeclarationList();
				setState(660);
				match(T__21);
				}
				break;
			}
			setState(666);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__8) {
				{
				setState(664);
				match(T__8);
				setState(665);
				expression(0);
				}
			}

			setState(668);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VariableDeclarationListContext extends ParserRuleContext {
		public List<VariableDeclarationContext> variableDeclaration() {
			return getRuleContexts(VariableDeclarationContext.class);
		}
		public VariableDeclarationContext variableDeclaration(int i) {
			return getRuleContext(VariableDeclarationContext.class,i);
		}
		public VariableDeclarationListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variableDeclarationList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitVariableDeclarationList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VariableDeclarationListContext variableDeclarationList() throws RecognitionException {
		VariableDeclarationListContext _localctx = new VariableDeclarationListContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_variableDeclarationList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(671);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__33) | (1L << T__35) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (Identifier - 91)))) != 0)) {
				{
				setState(670);
				variableDeclaration();
				}
			}

			setState(679);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(673);
				match(T__14);
				setState(675);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__33) | (1L << T__35) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (Identifier - 91)))) != 0)) {
					{
					setState(674);
					variableDeclaration();
					}
				}

				}
				}
				setState(681);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IdentifierListContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public IdentifierListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifierList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitIdentifierList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IdentifierListContext identifierList() throws RecognitionException {
		IdentifierListContext _localctx = new IdentifierListContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_identifierList);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(682);
			match(T__20);
			setState(689);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,74,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(684);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__12 || _la==T__39 || _la==Identifier) {
						{
						setState(683);
						identifier();
						}
					}

					setState(686);
					match(T__14);
					}
					} 
				}
				setState(691);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,74,_ctx);
			}
			setState(693);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__12 || _la==T__39 || _la==Identifier) {
				{
				setState(692);
				identifier();
				}
			}

			setState(695);
			match(T__21);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ElementaryTypeNameContext extends ParserRuleContext {
		public TerminalNode Int() { return getToken(SolidityParser.Int, 0); }
		public TerminalNode Uint() { return getToken(SolidityParser.Uint, 0); }
		public TerminalNode Byte() { return getToken(SolidityParser.Byte, 0); }
		public TerminalNode Fixed() { return getToken(SolidityParser.Fixed, 0); }
		public TerminalNode Ufixed() { return getToken(SolidityParser.Ufixed, 0); }
		public ElementaryTypeNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_elementaryTypeName; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitElementaryTypeName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ElementaryTypeNameContext elementaryTypeName() throws RecognitionException {
		ElementaryTypeNameContext _localctx = new ElementaryTypeNameContext(_ctx, getState());
		enterRule(_localctx, 118, RULE_elementaryTypeName);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(697);
			_la = _input.LA(1);
			if ( !(((((_la - 34)) & ~0x3f) == 0 && ((1L << (_la - 34)) & ((1L << (T__33 - 34)) | (1L << (T__48 - 34)) | (1L << (T__49 - 34)) | (1L << (T__50 - 34)) | (1L << (T__51 - 34)) | (1L << (Int - 34)) | (1L << (Uint - 34)) | (1L << (Byte - 34)) | (1L << (Fixed - 34)) | (1L << (Ufixed - 34)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionContext extends ParserRuleContext {
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public PrimaryExpressionContext primaryExpression() {
			return getRuleContext(PrimaryExpressionContext.class,0);
		}
		public FunctionCallArgumentsContext functionCallArguments() {
			return getRuleContext(FunctionCallArgumentsContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		return expression(0);
	}

	private ExpressionContext expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExpressionContext _localctx = new ExpressionContext(_ctx, _parentState);
		ExpressionContext _prevctx = _localctx;
		int _startState = 120;
		enterRecursionRule(_localctx, 120, RULE_expression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(717);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,76,_ctx) ) {
			case 1:
				{
				setState(700);
				match(T__54);
				setState(701);
				typeName(0);
				}
				break;
			case 2:
				{
				setState(702);
				match(T__20);
				setState(703);
				expression(0);
				setState(704);
				match(T__21);
				}
				break;
			case 3:
				{
				setState(706);
				_la = _input.LA(1);
				if ( !(_la==T__52 || _la==T__53) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(707);
				expression(19);
				}
				break;
			case 4:
				{
				setState(708);
				_la = _input.LA(1);
				if ( !(_la==T__55 || _la==T__56) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(709);
				expression(18);
				}
				break;
			case 5:
				{
				setState(710);
				_la = _input.LA(1);
				if ( !(_la==T__57 || _la==T__58) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(711);
				expression(17);
				}
				break;
			case 6:
				{
				setState(712);
				match(T__59);
				setState(713);
				expression(16);
				}
				break;
			case 7:
				{
				setState(714);
				match(T__3);
				setState(715);
				expression(15);
				}
				break;
			case 8:
				{
				setState(716);
				primaryExpression();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(778);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,78,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(776);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,77,_ctx) ) {
					case 1:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(719);
						if (!(precpred(_ctx, 14))) throw new FailedPredicateException(this, "precpred(_ctx, 14)");
						setState(720);
						match(T__60);
						setState(721);
						expression(15);
						}
						break;
					case 2:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(722);
						if (!(precpred(_ctx, 13))) throw new FailedPredicateException(this, "precpred(_ctx, 13)");
						setState(723);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__11) | (1L << T__61) | (1L << T__62))) != 0)) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(724);
						expression(14);
						}
						break;
					case 3:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(725);
						if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
						setState(726);
						_la = _input.LA(1);
						if ( !(_la==T__55 || _la==T__56) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(727);
						expression(13);
						}
						break;
					case 4:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(728);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(729);
						_la = _input.LA(1);
						if ( !(_la==T__63 || _la==T__64) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(730);
						expression(12);
						}
						break;
					case 5:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(731);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(732);
						match(T__65);
						setState(733);
						expression(11);
						}
						break;
					case 6:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(734);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(735);
						match(T__2);
						setState(736);
						expression(10);
						}
						break;
					case 7:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(737);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(738);
						match(T__66);
						setState(739);
						expression(9);
						}
						break;
					case 8:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(740);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(741);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7))) != 0)) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(742);
						expression(8);
						}
						break;
					case 9:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(743);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(744);
						_la = _input.LA(1);
						if ( !(_la==T__67 || _la==T__68) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(745);
						expression(7);
						}
						break;
					case 10:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(746);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(747);
						match(T__69);
						setState(748);
						expression(6);
						}
						break;
					case 11:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(749);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(750);
						match(T__70);
						setState(751);
						expression(5);
						}
						break;
					case 12:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(752);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(753);
						match(T__71);
						setState(754);
						expression(0);
						setState(755);
						match(T__72);
						setState(756);
						expression(4);
						}
						break;
					case 13:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(758);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(759);
						_la = _input.LA(1);
						if ( !(_la==T__8 || ((((_la - 74)) & ~0x3f) == 0 && ((1L << (_la - 74)) & ((1L << (T__73 - 74)) | (1L << (T__74 - 74)) | (1L << (T__75 - 74)) | (1L << (T__76 - 74)) | (1L << (T__77 - 74)) | (1L << (T__78 - 74)) | (1L << (T__79 - 74)) | (1L << (T__80 - 74)) | (1L << (T__81 - 74)) | (1L << (T__82 - 74)))) != 0)) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(760);
						expression(3);
						}
						break;
					case 14:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(761);
						if (!(precpred(_ctx, 25))) throw new FailedPredicateException(this, "precpred(_ctx, 25)");
						setState(762);
						_la = _input.LA(1);
						if ( !(_la==T__52 || _la==T__53) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						}
						break;
					case 15:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(763);
						if (!(precpred(_ctx, 23))) throw new FailedPredicateException(this, "precpred(_ctx, 23)");
						setState(764);
						match(T__31);
						setState(765);
						expression(0);
						setState(766);
						match(T__32);
						}
						break;
					case 16:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(768);
						if (!(precpred(_ctx, 22))) throw new FailedPredicateException(this, "precpred(_ctx, 22)");
						setState(769);
						match(T__20);
						setState(770);
						functionCallArguments();
						setState(771);
						match(T__21);
						}
						break;
					case 17:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(773);
						if (!(precpred(_ctx, 21))) throw new FailedPredicateException(this, "precpred(_ctx, 21)");
						setState(774);
						match(T__34);
						setState(775);
						identifier();
						}
						break;
					}
					} 
				}
				setState(780);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,78,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class ConstantExpressionContext extends ParserRuleContext {
		public ConstantExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constantExpression; }
	 
		public ConstantExpressionContext() { }
		public void copyFrom(ConstantExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class UnaryOpContext extends ConstantExpressionContext {
		public Token op;
		public ConstantExpressionContext constantExpression() {
			return getRuleContext(ConstantExpressionContext.class,0);
		}
		public UnaryOpContext(ConstantExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitUnaryOp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class NumberContext extends ConstantExpressionContext {
		public NumberLiteralNoUnitContext numberLiteralNoUnit() {
			return getRuleContext(NumberLiteralNoUnitContext.class,0);
		}
		public NumberContext(ConstantExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitNumber(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ParensContext extends ConstantExpressionContext {
		public ConstantExpressionContext constantExpression() {
			return getRuleContext(ConstantExpressionContext.class,0);
		}
		public ParensContext(ConstantExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitParens(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BinaryOpContext extends ConstantExpressionContext {
		public Token op;
		public List<ConstantExpressionContext> constantExpression() {
			return getRuleContexts(ConstantExpressionContext.class);
		}
		public ConstantExpressionContext constantExpression(int i) {
			return getRuleContext(ConstantExpressionContext.class,i);
		}
		public BinaryOpContext(ConstantExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitBinaryOp(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConstantExpressionContext constantExpression() throws RecognitionException {
		return constantExpression(0);
	}

	private ConstantExpressionContext constantExpression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ConstantExpressionContext _localctx = new ConstantExpressionContext(_ctx, _parentState);
		ConstantExpressionContext _prevctx = _localctx;
		int _startState = 122;
		enterRecursionRule(_localctx, 122, RULE_constantExpression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(789);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__55:
			case T__56:
				{
				_localctx = new UnaryOpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(782);
				((UnaryOpContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__55 || _la==T__56) ) {
					((UnaryOpContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(783);
				constantExpression(7);
				}
				break;
			case T__20:
				{
				_localctx = new ParensContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(784);
				match(T__20);
				setState(785);
				constantExpression(0);
				setState(786);
				match(T__21);
				}
				break;
			case DecimalNumber:
			case HexNumber:
				{
				_localctx = new NumberContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(788);
				numberLiteralNoUnit();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(805);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,81,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(803);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,80,_ctx) ) {
					case 1:
						{
						_localctx = new BinaryOpContext(new ConstantExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_constantExpression);
						setState(791);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(792);
						((BinaryOpContext)_localctx).op = match(T__60);
						setState(793);
						constantExpression(5);
						}
						break;
					case 2:
						{
						_localctx = new BinaryOpContext(new ConstantExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_constantExpression);
						setState(794);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(795);
						((BinaryOpContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__11) | (1L << T__61) | (1L << T__62))) != 0)) ) {
							((BinaryOpContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(796);
						constantExpression(5);
						}
						break;
					case 3:
						{
						_localctx = new BinaryOpContext(new ConstantExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_constantExpression);
						setState(797);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(798);
						((BinaryOpContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__55 || _la==T__56) ) {
							((BinaryOpContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(799);
						constantExpression(4);
						}
						break;
					case 4:
						{
						_localctx = new BinaryOpContext(new ConstantExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_constantExpression);
						setState(800);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(801);
						((BinaryOpContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__63 || _la==T__64) ) {
							((BinaryOpContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(802);
						constantExpression(3);
						}
						break;
					}
					} 
				}
				setState(807);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,81,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class PrimaryExpressionContext extends ParserRuleContext {
		public TerminalNode BooleanLiteral() { return getToken(SolidityParser.BooleanLiteral, 0); }
		public NumberLiteralContext numberLiteral() {
			return getRuleContext(NumberLiteralContext.class,0);
		}
		public TerminalNode HexLiteral() { return getToken(SolidityParser.HexLiteral, 0); }
		public TerminalNode StringLiteral() { return getToken(SolidityParser.StringLiteral, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TupleExpressionContext tupleExpression() {
			return getRuleContext(TupleExpressionContext.class,0);
		}
		public ElementaryTypeNameExpressionContext elementaryTypeNameExpression() {
			return getRuleContext(ElementaryTypeNameExpressionContext.class,0);
		}
		public PrimaryExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_primaryExpression; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitPrimaryExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PrimaryExpressionContext primaryExpression() throws RecognitionException {
		PrimaryExpressionContext _localctx = new PrimaryExpressionContext(_ctx, getState());
		enterRule(_localctx, 124, RULE_primaryExpression);
		try {
			setState(815);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BooleanLiteral:
				enterOuterAlt(_localctx, 1);
				{
				setState(808);
				match(BooleanLiteral);
				}
				break;
			case DecimalNumber:
			case HexNumber:
				enterOuterAlt(_localctx, 2);
				{
				setState(809);
				numberLiteral();
				}
				break;
			case HexLiteral:
				enterOuterAlt(_localctx, 3);
				{
				setState(810);
				match(HexLiteral);
				}
				break;
			case StringLiteral:
				enterOuterAlt(_localctx, 4);
				{
				setState(811);
				match(StringLiteral);
				}
				break;
			case T__12:
			case T__39:
			case Identifier:
				enterOuterAlt(_localctx, 5);
				{
				setState(812);
				identifier();
				}
				break;
			case T__20:
			case T__31:
				enterOuterAlt(_localctx, 6);
				{
				setState(813);
				tupleExpression();
				}
				break;
			case T__33:
			case T__48:
			case T__49:
			case T__50:
			case T__51:
			case Int:
			case Uint:
			case Byte:
			case Fixed:
			case Ufixed:
				enterOuterAlt(_localctx, 7);
				{
				setState(814);
				elementaryTypeNameExpression();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionListContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ExpressionListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expressionList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitExpressionList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionListContext expressionList() throws RecognitionException {
		ExpressionListContext _localctx = new ExpressionListContext(_ctx, getState());
		enterRule(_localctx, 126, RULE_expressionList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(817);
			expression(0);
			setState(822);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(818);
				match(T__14);
				setState(819);
				expression(0);
				}
				}
				setState(824);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NameValueListContext extends ParserRuleContext {
		public List<NameValueContext> nameValue() {
			return getRuleContexts(NameValueContext.class);
		}
		public NameValueContext nameValue(int i) {
			return getRuleContext(NameValueContext.class,i);
		}
		public NameValueListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nameValueList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitNameValueList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NameValueListContext nameValueList() throws RecognitionException {
		NameValueListContext _localctx = new NameValueListContext(_ctx, getState());
		enterRule(_localctx, 128, RULE_nameValueList);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(825);
			nameValue();
			setState(830);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,84,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(826);
					match(T__14);
					setState(827);
					nameValue();
					}
					} 
				}
				setState(832);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,84,_ctx);
			}
			setState(834);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__14) {
				{
				setState(833);
				match(T__14);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NameValueContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NameValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nameValue; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitNameValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NameValueContext nameValue() throws RecognitionException {
		NameValueContext _localctx = new NameValueContext(_ctx, getState());
		enterRule(_localctx, 130, RULE_nameValue);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(836);
			identifier();
			setState(837);
			match(T__72);
			setState(838);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionCallArgumentsContext extends ParserRuleContext {
		public NameValueListContext nameValueList() {
			return getRuleContext(NameValueListContext.class,0);
		}
		public ExpressionListContext expressionList() {
			return getRuleContext(ExpressionListContext.class,0);
		}
		public FunctionCallArgumentsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionCallArguments; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitFunctionCallArguments(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionCallArgumentsContext functionCallArguments() throws RecognitionException {
		FunctionCallArgumentsContext _localctx = new FunctionCallArgumentsContext(_ctx, getState());
		enterRule(_localctx, 132, RULE_functionCallArguments);
		int _la;
		try {
			setState(848);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__13:
				enterOuterAlt(_localctx, 1);
				{
				setState(840);
				match(T__13);
				setState(842);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__12 || _la==T__39 || _la==Identifier) {
					{
					setState(841);
					nameValueList();
					}
				}

				setState(844);
				match(T__15);
				}
				break;
			case T__3:
			case T__12:
			case T__20:
			case T__21:
			case T__31:
			case T__33:
			case T__39:
			case T__48:
			case T__49:
			case T__50:
			case T__51:
			case T__52:
			case T__53:
			case T__54:
			case T__55:
			case T__56:
			case T__57:
			case T__58:
			case T__59:
			case Int:
			case Uint:
			case Byte:
			case Fixed:
			case Ufixed:
			case BooleanLiteral:
			case DecimalNumber:
			case HexNumber:
			case HexLiteral:
			case Identifier:
			case StringLiteral:
				enterOuterAlt(_localctx, 2);
				{
				setState(846);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__20) | (1L << T__31) | (1L << T__33) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
					{
					setState(845);
					expressionList();
					}
				}

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionCallContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public FunctionCallArgumentsContext functionCallArguments() {
			return getRuleContext(FunctionCallArgumentsContext.class,0);
		}
		public FunctionCallContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionCall; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitFunctionCall(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionCallContext functionCall() throws RecognitionException {
		FunctionCallContext _localctx = new FunctionCallContext(_ctx, getState());
		enterRule(_localctx, 134, RULE_functionCall);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(850);
			expression(0);
			setState(851);
			match(T__20);
			setState(852);
			functionCallArguments();
			setState(853);
			match(T__21);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyBlockContext extends ParserRuleContext {
		public List<AssemblyItemContext> assemblyItem() {
			return getRuleContexts(AssemblyItemContext.class);
		}
		public AssemblyItemContext assemblyItem(int i) {
			return getRuleContext(AssemblyItemContext.class,i);
		}
		public AssemblyBlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyBlock; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyBlockContext assemblyBlock() throws RecognitionException {
		AssemblyBlockContext _localctx = new AssemblyBlockContext(_ctx, getState());
		enterRule(_localctx, 136, RULE_assemblyBlock);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(855);
			match(T__13);
			setState(859);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__13) | (1L << T__20) | (1L << T__23) | (1L << T__27) | (1L << T__33) | (1L << T__39) | (1L << T__40) | (1L << T__43) | (1L << T__45) | (1L << T__51))) != 0) || ((((_la - 84)) & ~0x3f) == 0 && ((1L << (_la - 84)) & ((1L << (T__83 - 84)) | (1L << (T__85 - 84)) | (1L << (T__86 - 84)) | (1L << (DecimalNumber - 84)) | (1L << (HexNumber - 84)) | (1L << (HexLiteral - 84)) | (1L << (BreakKeyword - 84)) | (1L << (ContinueKeyword - 84)) | (1L << (Identifier - 84)) | (1L << (StringLiteral - 84)))) != 0)) {
				{
				{
				setState(856);
				assemblyItem();
				}
				}
				setState(861);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(862);
			match(T__15);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyItemContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public AssemblyBlockContext assemblyBlock() {
			return getRuleContext(AssemblyBlockContext.class,0);
		}
		public AssemblyExpressionContext assemblyExpression() {
			return getRuleContext(AssemblyExpressionContext.class,0);
		}
		public AssemblyLocalDefinitionContext assemblyLocalDefinition() {
			return getRuleContext(AssemblyLocalDefinitionContext.class,0);
		}
		public AssemblyAssignmentContext assemblyAssignment() {
			return getRuleContext(AssemblyAssignmentContext.class,0);
		}
		public AssemblyStackAssignmentContext assemblyStackAssignment() {
			return getRuleContext(AssemblyStackAssignmentContext.class,0);
		}
		public LabelDefinitionContext labelDefinition() {
			return getRuleContext(LabelDefinitionContext.class,0);
		}
		public AssemblySwitchContext assemblySwitch() {
			return getRuleContext(AssemblySwitchContext.class,0);
		}
		public AssemblyFunctionDefinitionContext assemblyFunctionDefinition() {
			return getRuleContext(AssemblyFunctionDefinitionContext.class,0);
		}
		public AssemblyForContext assemblyFor() {
			return getRuleContext(AssemblyForContext.class,0);
		}
		public AssemblyIfContext assemblyIf() {
			return getRuleContext(AssemblyIfContext.class,0);
		}
		public TerminalNode BreakKeyword() { return getToken(SolidityParser.BreakKeyword, 0); }
		public TerminalNode ContinueKeyword() { return getToken(SolidityParser.ContinueKeyword, 0); }
		public SubAssemblyContext subAssembly() {
			return getRuleContext(SubAssemblyContext.class,0);
		}
		public NumberLiteralContext numberLiteral() {
			return getRuleContext(NumberLiteralContext.class,0);
		}
		public TerminalNode StringLiteral() { return getToken(SolidityParser.StringLiteral, 0); }
		public TerminalNode HexLiteral() { return getToken(SolidityParser.HexLiteral, 0); }
		public AssemblyItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyItem; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyItem(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyItemContext assemblyItem() throws RecognitionException {
		AssemblyItemContext _localctx = new AssemblyItemContext(_ctx, getState());
		enterRule(_localctx, 138, RULE_assemblyItem);
		try {
			setState(881);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,90,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(864);
				identifier();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(865);
				assemblyBlock();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(866);
				assemblyExpression();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(867);
				assemblyLocalDefinition();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(868);
				assemblyAssignment();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(869);
				assemblyStackAssignment();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(870);
				labelDefinition();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(871);
				assemblySwitch();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(872);
				assemblyFunctionDefinition();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(873);
				assemblyFor();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(874);
				assemblyIf();
				}
				break;
			case 12:
				enterOuterAlt(_localctx, 12);
				{
				setState(875);
				match(BreakKeyword);
				}
				break;
			case 13:
				enterOuterAlt(_localctx, 13);
				{
				setState(876);
				match(ContinueKeyword);
				}
				break;
			case 14:
				enterOuterAlt(_localctx, 14);
				{
				setState(877);
				subAssembly();
				}
				break;
			case 15:
				enterOuterAlt(_localctx, 15);
				{
				setState(878);
				numberLiteral();
				}
				break;
			case 16:
				enterOuterAlt(_localctx, 16);
				{
				setState(879);
				match(StringLiteral);
				}
				break;
			case 17:
				enterOuterAlt(_localctx, 17);
				{
				setState(880);
				match(HexLiteral);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyExpressionContext extends ParserRuleContext {
		public AssemblyCallContext assemblyCall() {
			return getRuleContext(AssemblyCallContext.class,0);
		}
		public AssemblyLiteralContext assemblyLiteral() {
			return getRuleContext(AssemblyLiteralContext.class,0);
		}
		public AssemblyExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyExpression; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyExpressionContext assemblyExpression() throws RecognitionException {
		AssemblyExpressionContext _localctx = new AssemblyExpressionContext(_ctx, getState());
		enterRule(_localctx, 140, RULE_assemblyExpression);
		try {
			setState(885);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__12:
			case T__33:
			case T__39:
			case T__45:
			case T__51:
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(883);
				assemblyCall();
				}
				break;
			case DecimalNumber:
			case HexNumber:
			case HexLiteral:
			case StringLiteral:
				enterOuterAlt(_localctx, 2);
				{
				setState(884);
				assemblyLiteral();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyCallContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public List<AssemblyExpressionContext> assemblyExpression() {
			return getRuleContexts(AssemblyExpressionContext.class);
		}
		public AssemblyExpressionContext assemblyExpression(int i) {
			return getRuleContext(AssemblyExpressionContext.class,i);
		}
		public AssemblyCallContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyCall; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyCall(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyCallContext assemblyCall() throws RecognitionException {
		AssemblyCallContext _localctx = new AssemblyCallContext(_ctx, getState());
		enterRule(_localctx, 142, RULE_assemblyCall);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(891);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__45:
				{
				setState(887);
				match(T__45);
				}
				break;
			case T__33:
				{
				setState(888);
				match(T__33);
				}
				break;
			case T__51:
				{
				setState(889);
				match(T__51);
				}
				break;
			case T__12:
			case T__39:
			case Identifier:
				{
				setState(890);
				identifier();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(905);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,95,_ctx) ) {
			case 1:
				{
				setState(893);
				match(T__20);
				setState(895);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__12) | (1L << T__33) | (1L << T__39) | (1L << T__45) | (1L << T__51))) != 0) || ((((_la - 98)) & ~0x3f) == 0 && ((1L << (_la - 98)) & ((1L << (DecimalNumber - 98)) | (1L << (HexNumber - 98)) | (1L << (HexLiteral - 98)) | (1L << (Identifier - 98)) | (1L << (StringLiteral - 98)))) != 0)) {
					{
					setState(894);
					assemblyExpression();
					}
				}

				setState(901);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__14) {
					{
					{
					setState(897);
					match(T__14);
					setState(898);
					assemblyExpression();
					}
					}
					setState(903);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(904);
				match(T__21);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyLocalDefinitionContext extends ParserRuleContext {
		public AssemblyIdentifierOrListContext assemblyIdentifierOrList() {
			return getRuleContext(AssemblyIdentifierOrListContext.class,0);
		}
		public AssemblyExpressionContext assemblyExpression() {
			return getRuleContext(AssemblyExpressionContext.class,0);
		}
		public AssemblyLocalDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyLocalDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyLocalDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyLocalDefinitionContext assemblyLocalDefinition() throws RecognitionException {
		AssemblyLocalDefinitionContext _localctx = new AssemblyLocalDefinitionContext(_ctx, getState());
		enterRule(_localctx, 144, RULE_assemblyLocalDefinition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(907);
			match(T__83);
			setState(908);
			assemblyIdentifierOrList();
			setState(911);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__84) {
				{
				setState(909);
				match(T__84);
				setState(910);
				assemblyExpression();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyAssignmentContext extends ParserRuleContext {
		public AssemblyIdentifierOrListContext assemblyIdentifierOrList() {
			return getRuleContext(AssemblyIdentifierOrListContext.class,0);
		}
		public AssemblyExpressionContext assemblyExpression() {
			return getRuleContext(AssemblyExpressionContext.class,0);
		}
		public AssemblyAssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyAssignment; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyAssignment(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyAssignmentContext assemblyAssignment() throws RecognitionException {
		AssemblyAssignmentContext _localctx = new AssemblyAssignmentContext(_ctx, getState());
		enterRule(_localctx, 146, RULE_assemblyAssignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(913);
			assemblyIdentifierOrList();
			setState(914);
			match(T__84);
			setState(915);
			assemblyExpression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyIdentifierOrListContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public AssemblyIdentifierListContext assemblyIdentifierList() {
			return getRuleContext(AssemblyIdentifierListContext.class,0);
		}
		public AssemblyIdentifierOrListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyIdentifierOrList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyIdentifierOrList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyIdentifierOrListContext assemblyIdentifierOrList() throws RecognitionException {
		AssemblyIdentifierOrListContext _localctx = new AssemblyIdentifierOrListContext(_ctx, getState());
		enterRule(_localctx, 148, RULE_assemblyIdentifierOrList);
		try {
			setState(922);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__12:
			case T__39:
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(917);
				identifier();
				}
				break;
			case T__20:
				enterOuterAlt(_localctx, 2);
				{
				setState(918);
				match(T__20);
				setState(919);
				assemblyIdentifierList();
				setState(920);
				match(T__21);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyIdentifierListContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public AssemblyIdentifierListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyIdentifierList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyIdentifierList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyIdentifierListContext assemblyIdentifierList() throws RecognitionException {
		AssemblyIdentifierListContext _localctx = new AssemblyIdentifierListContext(_ctx, getState());
		enterRule(_localctx, 150, RULE_assemblyIdentifierList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(924);
			identifier();
			setState(929);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(925);
				match(T__14);
				setState(926);
				identifier();
				}
				}
				setState(931);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyStackAssignmentContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public AssemblyStackAssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyStackAssignment; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyStackAssignment(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyStackAssignmentContext assemblyStackAssignment() throws RecognitionException {
		AssemblyStackAssignmentContext _localctx = new AssemblyStackAssignmentContext(_ctx, getState());
		enterRule(_localctx, 152, RULE_assemblyStackAssignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(932);
			match(T__85);
			setState(933);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LabelDefinitionContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public LabelDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_labelDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitLabelDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LabelDefinitionContext labelDefinition() throws RecognitionException {
		LabelDefinitionContext _localctx = new LabelDefinitionContext(_ctx, getState());
		enterRule(_localctx, 154, RULE_labelDefinition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(935);
			identifier();
			setState(936);
			match(T__72);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblySwitchContext extends ParserRuleContext {
		public AssemblyExpressionContext assemblyExpression() {
			return getRuleContext(AssemblyExpressionContext.class,0);
		}
		public List<AssemblyCaseContext> assemblyCase() {
			return getRuleContexts(AssemblyCaseContext.class);
		}
		public AssemblyCaseContext assemblyCase(int i) {
			return getRuleContext(AssemblyCaseContext.class,i);
		}
		public AssemblySwitchContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblySwitch; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblySwitch(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblySwitchContext assemblySwitch() throws RecognitionException {
		AssemblySwitchContext _localctx = new AssemblySwitchContext(_ctx, getState());
		enterRule(_localctx, 156, RULE_assemblySwitch);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(938);
			match(T__86);
			setState(939);
			assemblyExpression();
			setState(943);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__87 || _la==T__88) {
				{
				{
				setState(940);
				assemblyCase();
				}
				}
				setState(945);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyCaseContext extends ParserRuleContext {
		public AssemblyLiteralContext assemblyLiteral() {
			return getRuleContext(AssemblyLiteralContext.class,0);
		}
		public AssemblyBlockContext assemblyBlock() {
			return getRuleContext(AssemblyBlockContext.class,0);
		}
		public AssemblyCaseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyCase; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyCase(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyCaseContext assemblyCase() throws RecognitionException {
		AssemblyCaseContext _localctx = new AssemblyCaseContext(_ctx, getState());
		enterRule(_localctx, 158, RULE_assemblyCase);
		try {
			setState(952);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__87:
				enterOuterAlt(_localctx, 1);
				{
				setState(946);
				match(T__87);
				setState(947);
				assemblyLiteral();
				setState(948);
				assemblyBlock();
				}
				break;
			case T__88:
				enterOuterAlt(_localctx, 2);
				{
				setState(950);
				match(T__88);
				setState(951);
				assemblyBlock();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyFunctionDefinitionContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public AssemblyBlockContext assemblyBlock() {
			return getRuleContext(AssemblyBlockContext.class,0);
		}
		public AssemblyIdentifierListContext assemblyIdentifierList() {
			return getRuleContext(AssemblyIdentifierListContext.class,0);
		}
		public AssemblyFunctionReturnsContext assemblyFunctionReturns() {
			return getRuleContext(AssemblyFunctionReturnsContext.class,0);
		}
		public AssemblyFunctionDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyFunctionDefinition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyFunctionDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyFunctionDefinitionContext assemblyFunctionDefinition() throws RecognitionException {
		AssemblyFunctionDefinitionContext _localctx = new AssemblyFunctionDefinitionContext(_ctx, getState());
		enterRule(_localctx, 160, RULE_assemblyFunctionDefinition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(954);
			match(T__27);
			setState(955);
			identifier();
			setState(956);
			match(T__20);
			setState(958);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__12 || _la==T__39 || _la==Identifier) {
				{
				setState(957);
				assemblyIdentifierList();
				}
			}

			setState(960);
			match(T__21);
			setState(962);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__89) {
				{
				setState(961);
				assemblyFunctionReturns();
				}
			}

			setState(964);
			assemblyBlock();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyFunctionReturnsContext extends ParserRuleContext {
		public AssemblyIdentifierListContext assemblyIdentifierList() {
			return getRuleContext(AssemblyIdentifierListContext.class,0);
		}
		public AssemblyFunctionReturnsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyFunctionReturns; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyFunctionReturns(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyFunctionReturnsContext assemblyFunctionReturns() throws RecognitionException {
		AssemblyFunctionReturnsContext _localctx = new AssemblyFunctionReturnsContext(_ctx, getState());
		enterRule(_localctx, 162, RULE_assemblyFunctionReturns);
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(966);
			match(T__89);
			setState(967);
			assemblyIdentifierList();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyForContext extends ParserRuleContext {
		public List<AssemblyExpressionContext> assemblyExpression() {
			return getRuleContexts(AssemblyExpressionContext.class);
		}
		public AssemblyExpressionContext assemblyExpression(int i) {
			return getRuleContext(AssemblyExpressionContext.class,i);
		}
		public List<AssemblyBlockContext> assemblyBlock() {
			return getRuleContexts(AssemblyBlockContext.class);
		}
		public AssemblyBlockContext assemblyBlock(int i) {
			return getRuleContext(AssemblyBlockContext.class,i);
		}
		public AssemblyForContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyFor; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyFor(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyForContext assemblyFor() throws RecognitionException {
		AssemblyForContext _localctx = new AssemblyForContext(_ctx, getState());
		enterRule(_localctx, 164, RULE_assemblyFor);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(969);
			match(T__23);
			setState(972);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__13:
				{
				setState(970);
				assemblyBlock();
				}
				break;
			case T__12:
			case T__33:
			case T__39:
			case T__45:
			case T__51:
			case DecimalNumber:
			case HexNumber:
			case HexLiteral:
			case Identifier:
			case StringLiteral:
				{
				setState(971);
				assemblyExpression();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(974);
			assemblyExpression();
			setState(977);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__13:
				{
				setState(975);
				assemblyBlock();
				}
				break;
			case T__12:
			case T__33:
			case T__39:
			case T__45:
			case T__51:
			case DecimalNumber:
			case HexNumber:
			case HexLiteral:
			case Identifier:
			case StringLiteral:
				{
				setState(976);
				assemblyExpression();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(979);
			assemblyBlock();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyIfContext extends ParserRuleContext {
		public AssemblyExpressionContext assemblyExpression() {
			return getRuleContext(AssemblyExpressionContext.class,0);
		}
		public AssemblyBlockContext assemblyBlock() {
			return getRuleContext(AssemblyBlockContext.class,0);
		}
		public AssemblyIfContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyIf; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyIf(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyIfContext assemblyIf() throws RecognitionException {
		AssemblyIfContext _localctx = new AssemblyIfContext(_ctx, getState());
		enterRule(_localctx, 166, RULE_assemblyIf);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(981);
			match(T__40);
			setState(982);
			assemblyExpression();
			setState(983);
			assemblyBlock();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssemblyLiteralContext extends ParserRuleContext {
		public TerminalNode StringLiteral() { return getToken(SolidityParser.StringLiteral, 0); }
		public TerminalNode DecimalNumber() { return getToken(SolidityParser.DecimalNumber, 0); }
		public TerminalNode HexNumber() { return getToken(SolidityParser.HexNumber, 0); }
		public TerminalNode HexLiteral() { return getToken(SolidityParser.HexLiteral, 0); }
		public AssemblyLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assemblyLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitAssemblyLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssemblyLiteralContext assemblyLiteral() throws RecognitionException {
		AssemblyLiteralContext _localctx = new AssemblyLiteralContext(_ctx, getState());
		enterRule(_localctx, 168, RULE_assemblyLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(985);
			_la = _input.LA(1);
			if ( !(((((_la - 98)) & ~0x3f) == 0 && ((1L << (_la - 98)) & ((1L << (DecimalNumber - 98)) | (1L << (HexNumber - 98)) | (1L << (HexLiteral - 98)) | (1L << (StringLiteral - 98)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SubAssemblyContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public AssemblyBlockContext assemblyBlock() {
			return getRuleContext(AssemblyBlockContext.class,0);
		}
		public SubAssemblyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subAssembly; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitSubAssembly(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SubAssemblyContext subAssembly() throws RecognitionException {
		SubAssemblyContext _localctx = new SubAssemblyContext(_ctx, getState());
		enterRule(_localctx, 170, RULE_subAssembly);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(987);
			match(T__43);
			setState(988);
			identifier();
			setState(989);
			assemblyBlock();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TupleExpressionContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TupleExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tupleExpression; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitTupleExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TupleExpressionContext tupleExpression() throws RecognitionException {
		TupleExpressionContext _localctx = new TupleExpressionContext(_ctx, getState());
		enterRule(_localctx, 172, RULE_tupleExpression);
		int _la;
		try {
			setState(1017);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__20:
				enterOuterAlt(_localctx, 1);
				{
				setState(991);
				match(T__20);
				{
				setState(993);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__20) | (1L << T__31) | (1L << T__33) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
					{
					setState(992);
					expression(0);
					}
				}

				setState(1001);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__14) {
					{
					{
					setState(995);
					match(T__14);
					setState(997);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__20) | (1L << T__31) | (1L << T__33) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
						{
						setState(996);
						expression(0);
						}
					}

					}
					}
					setState(1003);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				setState(1004);
				match(T__21);
				}
				break;
			case T__31:
				enterOuterAlt(_localctx, 2);
				{
				setState(1005);
				match(T__31);
				setState(1014);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__12) | (1L << T__20) | (1L << T__31) | (1L << T__33) | (1L << T__39) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59))) != 0) || ((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (Int - 91)) | (1L << (Uint - 91)) | (1L << (Byte - 91)) | (1L << (Fixed - 91)) | (1L << (Ufixed - 91)) | (1L << (BooleanLiteral - 91)) | (1L << (DecimalNumber - 91)) | (1L << (HexNumber - 91)) | (1L << (HexLiteral - 91)) | (1L << (Identifier - 91)) | (1L << (StringLiteral - 91)))) != 0)) {
					{
					setState(1006);
					expression(0);
					setState(1011);
					_errHandler.sync(this);
					_la = _input.LA(1);
					while (_la==T__14) {
						{
						{
						setState(1007);
						match(T__14);
						setState(1008);
						expression(0);
						}
						}
						setState(1013);
						_errHandler.sync(this);
						_la = _input.LA(1);
					}
					}
				}

				setState(1016);
				match(T__32);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ElementaryTypeNameExpressionContext extends ParserRuleContext {
		public ElementaryTypeNameContext elementaryTypeName() {
			return getRuleContext(ElementaryTypeNameContext.class,0);
		}
		public ElementaryTypeNameExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_elementaryTypeNameExpression; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitElementaryTypeNameExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ElementaryTypeNameExpressionContext elementaryTypeNameExpression() throws RecognitionException {
		ElementaryTypeNameExpressionContext _localctx = new ElementaryTypeNameExpressionContext(_ctx, getState());
		enterRule(_localctx, 174, RULE_elementaryTypeNameExpression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1019);
			elementaryTypeName();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NumberLiteralContext extends ParserRuleContext {
		public NumberLiteralNoUnitContext numberLiteralNoUnit() {
			return getRuleContext(NumberLiteralNoUnitContext.class,0);
		}
		public TerminalNode NumberUnit() { return getToken(SolidityParser.NumberUnit, 0); }
		public NumberLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_numberLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitNumberLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NumberLiteralContext numberLiteral() throws RecognitionException {
		NumberLiteralContext _localctx = new NumberLiteralContext(_ctx, getState());
		enterRule(_localctx, 176, RULE_numberLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1021);
			numberLiteralNoUnit();
			setState(1023);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,111,_ctx) ) {
			case 1:
				{
				setState(1022);
				match(NumberUnit);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NumberLiteralNoUnitContext extends ParserRuleContext {
		public NumberLiteralNoUnitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_numberLiteralNoUnit; }
	 
		public NumberLiteralNoUnitContext() { }
		public void copyFrom(NumberLiteralNoUnitContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class DecimalContext extends NumberLiteralNoUnitContext {
		public TerminalNode DecimalNumber() { return getToken(SolidityParser.DecimalNumber, 0); }
		public DecimalContext(NumberLiteralNoUnitContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitDecimal(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class HexContext extends NumberLiteralNoUnitContext {
		public TerminalNode HexNumber() { return getToken(SolidityParser.HexNumber, 0); }
		public HexContext(NumberLiteralNoUnitContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitHex(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NumberLiteralNoUnitContext numberLiteralNoUnit() throws RecognitionException {
		NumberLiteralNoUnitContext _localctx = new NumberLiteralNoUnitContext(_ctx, getState());
		enterRule(_localctx, 178, RULE_numberLiteralNoUnit);
		try {
			setState(1027);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DecimalNumber:
				_localctx = new DecimalContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(1025);
				match(DecimalNumber);
				}
				break;
			case HexNumber:
				_localctx = new HexContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(1026);
				match(HexNumber);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IdentifierContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(SolidityParser.Identifier, 0); }
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolidityVisitor ) return ((SolidityVisitor<? extends T>)visitor).visitIdentifier(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 180, RULE_identifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1029);
			_la = _input.LA(1);
			if ( !(_la==T__12 || _la==T__39 || _la==Identifier) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 34:
			return typeName_sempred((TypeNameContext)_localctx, predIndex);
		case 35:
			return typeNameParam_sempred((TypeNameParamContext)_localctx, predIndex);
		case 60:
			return expression_sempred((ExpressionContext)_localctx, predIndex);
		case 61:
			return constantExpression_sempred((ConstantExpressionContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean typeName_sempred(TypeNameContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 3);
		}
		return true;
	}
	private boolean typeNameParam_sempred(TypeNameParamContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return precpred(_ctx, 1);
		}
		return true;
	}
	private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 2:
			return precpred(_ctx, 14);
		case 3:
			return precpred(_ctx, 13);
		case 4:
			return precpred(_ctx, 12);
		case 5:
			return precpred(_ctx, 11);
		case 6:
			return precpred(_ctx, 10);
		case 7:
			return precpred(_ctx, 9);
		case 8:
			return precpred(_ctx, 8);
		case 9:
			return precpred(_ctx, 7);
		case 10:
			return precpred(_ctx, 6);
		case 11:
			return precpred(_ctx, 5);
		case 12:
			return precpred(_ctx, 4);
		case 13:
			return precpred(_ctx, 3);
		case 14:
			return precpred(_ctx, 2);
		case 15:
			return precpred(_ctx, 25);
		case 16:
			return precpred(_ctx, 23);
		case 17:
			return precpred(_ctx, 22);
		case 18:
			return precpred(_ctx, 21);
		}
		return true;
	}
	private boolean constantExpression_sempred(ConstantExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 19:
			return precpred(_ctx, 5);
		case 20:
			return precpred(_ctx, 4);
		case 21:
			return precpred(_ctx, 3);
		case 22:
			return precpred(_ctx, 2);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3y\u040a\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t="+
		"\4>\t>\4?\t?\4@\t@\4A\tA\4B\tB\4C\tC\4D\tD\4E\tE\4F\tF\4G\tG\4H\tH\4I"+
		"\tI\4J\tJ\4K\tK\4L\tL\4M\tM\4N\tN\4O\tO\4P\tP\4Q\tQ\4R\tR\4S\tS\4T\tT"+
		"\4U\tU\4V\tV\4W\tW\4X\tX\4Y\tY\4Z\tZ\4[\t[\4\\\t\\\3\2\3\2\3\2\7\2\u00bc"+
		"\n\2\f\2\16\2\u00bf\13\2\3\2\3\2\3\3\3\3\3\3\3\3\3\3\3\4\3\4\3\5\3\5\5"+
		"\5\u00cc\n\5\3\6\3\6\5\6\u00d0\n\6\3\7\3\7\3\b\5\b\u00d5\n\b\3\b\3\b\3"+
		"\t\3\t\3\t\5\t\u00dc\n\t\3\n\3\n\3\n\3\n\5\n\u00e2\n\n\3\n\3\n\3\n\3\n"+
		"\5\n\u00e8\n\n\3\n\3\n\5\n\u00ec\n\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\7"+
		"\n\u00f6\n\n\f\n\16\n\u00f9\13\n\3\n\3\n\3\n\3\n\3\n\5\n\u0100\n\n\3\13"+
		"\3\13\3\13\3\13\3\13\3\13\7\13\u0108\n\13\f\13\16\13\u010b\13\13\5\13"+
		"\u010d\n\13\3\13\3\13\7\13\u0111\n\13\f\13\16\13\u0114\13\13\3\13\3\13"+
		"\3\f\3\f\3\f\3\f\3\f\7\f\u011d\n\f\f\f\16\f\u0120\13\f\3\f\3\f\5\f\u0124"+
		"\n\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\5\r\u012e\n\r\3\16\3\16\3\16\3\16"+
		"\3\16\5\16\u0135\n\16\3\16\3\16\3\17\5\17\u013a\n\17\3\17\3\17\5\17\u013e"+
		"\n\17\3\17\3\17\5\17\u0142\n\17\3\20\3\20\3\20\3\20\3\20\5\20\u0149\n"+
		"\20\3\20\3\20\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\7\21\u0155\n\21"+
		"\f\21\16\21\u0158\13\21\5\21\u015a\n\21\3\21\3\21\3\22\3\22\3\22\3\22"+
		"\3\22\3\23\3\23\3\23\5\23\u0166\n\23\3\23\3\23\3\24\3\24\3\24\5\24\u016d"+
		"\n\24\3\24\5\24\u0170\n\24\3\25\3\25\5\25\u0174\n\25\3\25\3\25\3\25\5"+
		"\25\u0179\n\25\3\25\3\25\5\25\u017d\n\25\3\26\3\26\3\26\3\27\3\27\3\27"+
		"\5\27\u0185\n\27\3\30\7\30\u0188\n\30\f\30\16\30\u018b\13\30\3\31\3\31"+
		"\3\31\3\31\5\31\u0191\n\31\3\31\3\31\3\32\3\32\3\33\3\33\3\33\3\33\5\33"+
		"\u019b\n\33\3\33\3\33\7\33\u019f\n\33\f\33\16\33\u01a2\13\33\3\33\3\33"+
		"\3\34\3\34\3\34\3\34\7\34\u01aa\n\34\f\34\16\34\u01ad\13\34\5\34\u01af"+
		"\n\34\3\34\3\34\3\35\3\35\5\35\u01b5\n\35\3\35\5\35\u01b8\n\35\3\36\3"+
		"\36\5\36\u01bc\n\36\3\36\5\36\u01bf\n\36\3\37\3\37\3\37\3\37\7\37\u01c5"+
		"\n\37\f\37\16\37\u01c8\13\37\5\37\u01ca\n\37\3\37\3\37\3 \3 \5 \u01d0"+
		"\n \3 \5 \u01d3\n \3!\3!\3!\3!\7!\u01d9\n!\f!\16!\u01dc\13!\5!\u01de\n"+
		"!\3!\3!\3\"\3\"\5\"\u01e4\n\"\3#\3#\5#\u01e8\n#\3#\3#\3$\3$\3$\3$\3$\3"+
		"$\3$\5$\u01f3\n$\3$\3$\3$\5$\u01f8\n$\3$\7$\u01fb\n$\f$\16$\u01fe\13$"+
		"\3%\3%\3%\3%\5%\u0204\n%\3%\3%\3%\5%\u0209\n%\3%\7%\u020c\n%\f%\16%\u020f"+
		"\13%\3&\3&\3&\7&\u0214\n&\f&\16&\u0217\13&\3\'\3\'\3\'\3\'\3\'\3\'\3\'"+
		"\3(\3(\3(\3(\3(\7(\u0225\n(\f(\16(\u0228\13(\3(\3(\5(\u022c\n(\3)\3)\3"+
		"*\3*\3+\3+\3,\3,\7,\u0236\n,\f,\16,\u0239\13,\3,\3,\3-\3-\3-\3-\3-\3-"+
		"\3-\3-\3-\3-\3-\3-\5-\u0249\n-\3.\3.\3.\3/\3/\3/\3/\3/\3/\3/\5/\u0255"+
		"\n/\3\60\3\60\3\60\3\60\3\60\3\60\3\61\3\61\5\61\u025f\n\61\3\62\3\62"+
		"\3\62\3\62\5\62\u0265\n\62\3\62\5\62\u0268\n\62\3\62\3\62\5\62\u026c\n"+
		"\62\3\62\3\62\3\62\3\63\3\63\5\63\u0273\n\63\3\63\3\63\3\64\3\64\3\64"+
		"\3\64\3\64\3\64\3\64\3\64\3\65\3\65\3\65\3\66\3\66\3\66\3\67\3\67\5\67"+
		"\u0287\n\67\3\67\3\67\38\38\38\39\39\39\39\3:\3:\3:\3:\3:\3:\3:\5:\u0299"+
		"\n:\3:\3:\5:\u029d\n:\3:\3:\3;\5;\u02a2\n;\3;\3;\5;\u02a6\n;\7;\u02a8"+
		"\n;\f;\16;\u02ab\13;\3<\3<\5<\u02af\n<\3<\7<\u02b2\n<\f<\16<\u02b5\13"+
		"<\3<\5<\u02b8\n<\3<\3<\3=\3=\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3"+
		">\3>\3>\3>\3>\5>\u02d0\n>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3"+
		">\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3"+
		">\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\3>\7>\u030b\n"+
		">\f>\16>\u030e\13>\3?\3?\3?\3?\3?\3?\3?\3?\5?\u0318\n?\3?\3?\3?\3?\3?"+
		"\3?\3?\3?\3?\3?\3?\3?\7?\u0326\n?\f?\16?\u0329\13?\3@\3@\3@\3@\3@\3@\3"+
		"@\5@\u0332\n@\3A\3A\3A\7A\u0337\nA\fA\16A\u033a\13A\3B\3B\3B\7B\u033f"+
		"\nB\fB\16B\u0342\13B\3B\5B\u0345\nB\3C\3C\3C\3C\3D\3D\5D\u034d\nD\3D\3"+
		"D\5D\u0351\nD\5D\u0353\nD\3E\3E\3E\3E\3E\3F\3F\7F\u035c\nF\fF\16F\u035f"+
		"\13F\3F\3F\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\5G\u0374"+
		"\nG\3H\3H\5H\u0378\nH\3I\3I\3I\3I\5I\u037e\nI\3I\3I\5I\u0382\nI\3I\3I"+
		"\7I\u0386\nI\fI\16I\u0389\13I\3I\5I\u038c\nI\3J\3J\3J\3J\5J\u0392\nJ\3"+
		"K\3K\3K\3K\3L\3L\3L\3L\3L\5L\u039d\nL\3M\3M\3M\7M\u03a2\nM\fM\16M\u03a5"+
		"\13M\3N\3N\3N\3O\3O\3O\3P\3P\3P\7P\u03b0\nP\fP\16P\u03b3\13P\3Q\3Q\3Q"+
		"\3Q\3Q\3Q\5Q\u03bb\nQ\3R\3R\3R\3R\5R\u03c1\nR\3R\3R\5R\u03c5\nR\3R\3R"+
		"\3S\3S\3S\3T\3T\3T\5T\u03cf\nT\3T\3T\3T\5T\u03d4\nT\3T\3T\3U\3U\3U\3U"+
		"\3V\3V\3W\3W\3W\3W\3X\3X\5X\u03e4\nX\3X\3X\5X\u03e8\nX\7X\u03ea\nX\fX"+
		"\16X\u03ed\13X\3X\3X\3X\3X\3X\7X\u03f4\nX\fX\16X\u03f7\13X\5X\u03f9\n"+
		"X\3X\5X\u03fc\nX\3Y\3Y\3Z\3Z\5Z\u0402\nZ\3[\3[\5[\u0406\n[\3\\\3\\\3\\"+
		"\2\6FHz|]\2\4\6\b\n\f\16\20\22\24\26\30\32\34\36 \"$&(*,.\60\62\64\66"+
		"8:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprtvxz|~\u0080\u0082\u0084\u0086\u0088\u008a"+
		"\u008c\u008e\u0090\u0092\u0094\u0096\u0098\u009a\u009c\u009e\u00a0\u00a2"+
		"\u00a4\u00a6\u00a8\u00aa\u00ac\u00ae\u00b0\u00b2\u00b4\u00b6\2\23\3\2"+
		"\5\13\3\2\23\25\4\2ooqr\3\2(*\5\2mmooqr\5\2kkppst\5\2$$\63\66]a\3\2\67"+
		"8\3\2:;\3\2<=\4\2\16\16@A\3\2BC\3\2\7\n\3\2FG\4\2\13\13LU\5\2deggvv\5"+
		"\2\17\17**uu\2\u0467\2\u00bd\3\2\2\2\4\u00c2\3\2\2\2\6\u00c7\3\2\2\2\b"+
		"\u00cb\3\2\2\2\n\u00cd\3\2\2\2\f\u00d1\3\2\2\2\16\u00d4\3\2\2\2\20\u00d8"+
		"\3\2\2\2\22\u00ff\3\2\2\2\24\u0101\3\2\2\2\26\u0117\3\2\2\2\30\u012d\3"+
		"\2\2\2\32\u012f\3\2\2\2\34\u0141\3\2\2\2\36\u0143\3\2\2\2 \u014c\3\2\2"+
		"\2\"\u015d\3\2\2\2$\u0162\3\2\2\2&\u0169\3\2\2\2(\u0171\3\2\2\2*\u017e"+
		"\3\2\2\2,\u0184\3\2\2\2.\u0189\3\2\2\2\60\u018c\3\2\2\2\62\u0194\3\2\2"+
		"\2\64\u0196\3\2\2\2\66\u01a5\3\2\2\28\u01b2\3\2\2\2:\u01b9\3\2\2\2<\u01c0"+
		"\3\2\2\2>\u01cd\3\2\2\2@\u01d4\3\2\2\2B\u01e1\3\2\2\2D\u01e5\3\2\2\2F"+
		"\u01f2\3\2\2\2H\u0203\3\2\2\2J\u0210\3\2\2\2L\u0218\3\2\2\2N\u021f\3\2"+
		"\2\2P\u022d\3\2\2\2R\u022f\3\2\2\2T\u0231\3\2\2\2V\u0233\3\2\2\2X\u0248"+
		"\3\2\2\2Z\u024a\3\2\2\2\\\u024d\3\2\2\2^\u0256\3\2\2\2`\u025e\3\2\2\2"+
		"b\u0260\3\2\2\2d\u0270\3\2\2\2f\u0276\3\2\2\2h\u027e\3\2\2\2j\u0281\3"+
		"\2\2\2l\u0284\3\2\2\2n\u028a\3\2\2\2p\u028d\3\2\2\2r\u0298\3\2\2\2t\u02a1"+
		"\3\2\2\2v\u02ac\3\2\2\2x\u02bb\3\2\2\2z\u02cf\3\2\2\2|\u0317\3\2\2\2~"+
		"\u0331\3\2\2\2\u0080\u0333\3\2\2\2\u0082\u033b\3\2\2\2\u0084\u0346\3\2"+
		"\2\2\u0086\u0352\3\2\2\2\u0088\u0354\3\2\2\2\u008a\u0359\3\2\2\2\u008c"+
		"\u0373\3\2\2\2\u008e\u0377\3\2\2\2\u0090\u037d\3\2\2\2\u0092\u038d\3\2"+
		"\2\2\u0094\u0393\3\2\2\2\u0096\u039c\3\2\2\2\u0098\u039e\3\2\2\2\u009a"+
		"\u03a6\3\2\2\2\u009c\u03a9\3\2\2\2\u009e\u03ac\3\2\2\2\u00a0\u03ba\3\2"+
		"\2\2\u00a2\u03bc\3\2\2\2\u00a4\u03c8\3\2\2\2\u00a6\u03cb\3\2\2\2\u00a8"+
		"\u03d7\3\2\2\2\u00aa\u03db\3\2\2\2\u00ac\u03dd\3\2\2\2\u00ae\u03fb\3\2"+
		"\2\2\u00b0\u03fd\3\2\2\2\u00b2\u03ff\3\2\2\2\u00b4\u0405\3\2\2\2\u00b6"+
		"\u0407\3\2\2\2\u00b8\u00bc\5\4\3\2\u00b9\u00bc\5\22\n\2\u00ba\u00bc\5"+
		"\24\13\2\u00bb\u00b8\3\2\2\2\u00bb\u00b9\3\2\2\2\u00bb\u00ba\3\2\2\2\u00bc"+
		"\u00bf\3\2\2\2\u00bd\u00bb\3\2\2\2\u00bd\u00be\3\2\2\2\u00be\u00c0\3\2"+
		"\2\2\u00bf\u00bd\3\2\2\2\u00c0\u00c1\7\2\2\3\u00c1\3\3\2\2\2\u00c2\u00c3"+
		"\7\3\2\2\u00c3\u00c4\5\6\4\2\u00c4\u00c5\5\b\5\2\u00c5\u00c6\7\4\2\2\u00c6"+
		"\5\3\2\2\2\u00c7\u00c8\5\u00b6\\\2\u00c8\7\3\2\2\2\u00c9\u00cc\5\n\6\2"+
		"\u00ca\u00cc\5z>\2\u00cb\u00c9\3\2\2\2\u00cb\u00ca\3\2\2\2\u00cc\t\3\2"+
		"\2\2\u00cd\u00cf\5\16\b\2\u00ce\u00d0\5\16\b\2\u00cf\u00ce\3\2\2\2\u00cf"+
		"\u00d0\3\2\2\2\u00d0\13\3\2\2\2\u00d1\u00d2\t\2\2\2\u00d2\r\3\2\2\2\u00d3"+
		"\u00d5\5\f\7\2\u00d4\u00d3\3\2\2\2\u00d4\u00d5\3\2\2\2\u00d5\u00d6\3\2"+
		"\2\2\u00d6\u00d7\7b\2\2\u00d7\17\3\2\2\2\u00d8\u00db\5\u00b6\\\2\u00d9"+
		"\u00da\7\f\2\2\u00da\u00dc\5\u00b6\\\2\u00db\u00d9\3\2\2\2\u00db\u00dc"+
		"\3\2\2\2\u00dc\21\3\2\2\2\u00dd\u00de\7\r\2\2\u00de\u00e1\7v\2\2\u00df"+
		"\u00e0\7\f\2\2\u00e0\u00e2\5\u00b6\\\2\u00e1\u00df\3\2\2\2\u00e1\u00e2"+
		"\3\2\2\2\u00e2\u00e3\3\2\2\2\u00e3\u0100\7\4\2\2\u00e4\u00e7\7\r\2\2\u00e5"+
		"\u00e8\7\16\2\2\u00e6\u00e8\5\u00b6\\\2\u00e7\u00e5\3\2\2\2\u00e7\u00e6"+
		"\3\2\2\2\u00e8\u00eb\3\2\2\2\u00e9\u00ea\7\f\2\2\u00ea\u00ec\5\u00b6\\"+
		"\2\u00eb\u00e9\3\2\2\2\u00eb\u00ec\3\2\2\2\u00ec\u00ed\3\2\2\2\u00ed\u00ee"+
		"\7\17\2\2\u00ee\u00ef\7v\2\2\u00ef\u0100\7\4\2\2\u00f0\u00f1\7\r\2\2\u00f1"+
		"\u00f2\7\20\2\2\u00f2\u00f7\5\20\t\2\u00f3\u00f4\7\21\2\2\u00f4\u00f6"+
		"\5\20\t\2\u00f5\u00f3\3\2\2\2\u00f6\u00f9\3\2\2\2\u00f7\u00f5\3\2\2\2"+
		"\u00f7\u00f8\3\2\2\2\u00f8\u00fa\3\2\2\2\u00f9\u00f7\3\2\2\2\u00fa\u00fb"+
		"\7\22\2\2\u00fb\u00fc\7\17\2\2\u00fc\u00fd\7v\2\2\u00fd\u00fe\7\4\2\2"+
		"\u00fe\u0100\3\2\2\2\u00ff\u00dd\3\2\2\2\u00ff\u00e4\3\2\2\2\u00ff\u00f0"+
		"\3\2\2\2\u0100\23\3\2\2\2\u0101\u0102\t\3\2\2\u0102\u010c\5\u00b6\\\2"+
		"\u0103\u0104\7\26\2\2\u0104\u0109\5\26\f\2\u0105\u0106\7\21\2\2\u0106"+
		"\u0108\5\26\f\2\u0107\u0105\3\2\2\2\u0108\u010b\3\2\2\2\u0109\u0107\3"+
		"\2\2\2\u0109\u010a\3\2\2\2\u010a\u010d\3\2\2\2\u010b\u0109\3\2\2\2\u010c"+
		"\u0103\3\2\2\2\u010c\u010d\3\2\2\2\u010d\u010e\3\2\2\2\u010e\u0112\7\20"+
		"\2\2\u010f\u0111\5\30\r\2\u0110\u010f\3\2\2\2\u0111\u0114\3\2\2\2\u0112"+
		"\u0110\3\2\2\2\u0112\u0113\3\2\2\2\u0113\u0115\3\2\2\2\u0114\u0112\3\2"+
		"\2\2\u0115\u0116\7\22\2\2\u0116\25\3\2\2\2\u0117\u0123\5J&\2\u0118\u0119"+
		"\7\27\2\2\u0119\u011e\5z>\2\u011a\u011b\7\21\2\2\u011b\u011d\5z>\2\u011c"+
		"\u011a\3\2\2\2\u011d\u0120\3\2\2\2\u011e\u011c\3\2\2\2\u011e\u011f\3\2"+
		"\2\2\u011f\u0121\3\2\2\2\u0120\u011e\3\2\2\2\u0121\u0122\7\30\2\2\u0122"+
		"\u0124\3\2\2\2\u0123\u0118\3\2\2\2\u0123\u0124\3\2\2\2\u0124\27\3\2\2"+
		"\2\u0125\u012e\5\32\16\2\u0126\u012e\5\36\20\2\u0127\u012e\5 \21\2\u0128"+
		"\u012e\5\"\22\2\u0129\u012e\5$\23\2\u012a\u012e\5(\25\2\u012b\u012e\5"+
		"\60\31\2\u012c\u012e\5\64\33\2\u012d\u0125\3\2\2\2\u012d\u0126\3\2\2\2"+
		"\u012d\u0127\3\2\2\2\u012d\u0128\3\2\2\2\u012d\u0129\3\2\2\2\u012d\u012a"+
		"\3\2\2\2\u012d\u012b\3\2\2\2\u012d\u012c\3\2\2\2\u012e\31\3\2\2\2\u012f"+
		"\u0130\5H%\2\u0130\u0131\5\34\17\2\u0131\u0134\5\u00b6\\\2\u0132\u0133"+
		"\7\13\2\2\u0133\u0135\5z>\2\u0134\u0132\3\2\2\2\u0134\u0135\3\2\2\2\u0135"+
		"\u0136\3\2\2\2\u0136\u0137\7\4\2\2\u0137\33\3\2\2\2\u0138\u013a\t\4\2"+
		"\2\u0139\u0138\3\2\2\2\u0139\u013a\3\2\2\2\u013a\u013b\3\2\2\2\u013b\u0142"+
		"\7k\2\2\u013c\u013e\7k\2\2\u013d\u013c\3\2\2\2\u013d\u013e\3\2\2\2\u013e"+
		"\u013f\3\2\2\2\u013f\u0142\t\4\2\2\u0140\u0142\3\2\2\2\u0141\u0139\3\2"+
		"\2\2\u0141\u013d\3\2\2\2\u0141\u0140\3\2\2\2\u0142\35\3\2\2\2\u0143\u0144"+
		"\7\31\2\2\u0144\u0145\5\u00b6\\\2\u0145\u0148\7\32\2\2\u0146\u0149\7\16"+
		"\2\2\u0147\u0149\5F$\2\u0148\u0146\3\2\2\2\u0148\u0147\3\2\2\2\u0149\u014a"+
		"\3\2\2\2\u014a\u014b\7\4\2\2\u014b\37\3\2\2\2\u014c\u014d\7\33\2\2\u014d"+
		"\u014e\5\u00b6\\\2\u014e\u0159\7\20\2\2\u014f\u0150\5D#\2\u0150\u0156"+
		"\7\4\2\2\u0151\u0152\5D#\2\u0152\u0153\7\4\2\2\u0153\u0155\3\2\2\2\u0154"+
		"\u0151\3\2\2\2\u0155\u0158\3\2\2\2\u0156\u0154\3\2\2\2\u0156\u0157\3\2"+
		"\2\2\u0157\u015a\3\2\2\2\u0158\u0156\3\2\2\2\u0159\u014f\3\2\2\2\u0159"+
		"\u015a\3\2\2\2\u015a\u015b\3\2\2\2\u015b\u015c\7\22\2\2\u015c!\3\2\2\2"+
		"\u015d\u015e\7\34\2\2\u015e\u015f\5\66\34\2\u015f\u0160\5.\30\2\u0160"+
		"\u0161\5V,\2\u0161#\3\2\2\2\u0162\u0163\7\35\2\2\u0163\u0165\5\u00b6\\"+
		"\2\u0164\u0166\5\66\34\2\u0165\u0164\3\2\2\2\u0165\u0166\3\2\2\2\u0166"+
		"\u0167\3\2\2\2\u0167\u0168\5V,\2\u0168%\3\2\2\2\u0169\u016f\5\u00b6\\"+
		"\2\u016a\u016c\7\27\2\2\u016b\u016d\5\u0080A\2\u016c\u016b\3\2\2\2\u016c"+
		"\u016d\3\2\2\2\u016d\u016e\3\2\2\2\u016e\u0170\7\30\2\2\u016f\u016a\3"+
		"\2\2\2\u016f\u0170\3\2\2\2\u0170\'\3\2\2\2\u0171\u0173\7\36\2\2\u0172"+
		"\u0174\5\u00b6\\\2\u0173\u0172\3\2\2\2\u0173\u0174\3\2\2\2\u0174\u0175"+
		"\3\2\2\2\u0175\u0176\5\66\34\2\u0176\u0178\5.\30\2\u0177\u0179\5*\26\2"+
		"\u0178\u0177\3\2\2\2\u0178\u0179\3\2\2\2\u0179\u017c\3\2\2\2\u017a\u017d"+
		"\7\4\2\2\u017b\u017d\5V,\2\u017c\u017a\3\2\2\2\u017c\u017b\3\2\2\2\u017d"+
		")\3\2\2\2\u017e\u017f\7\37\2\2\u017f\u0180\5\66\34\2\u0180+\3\2\2\2\u0181"+
		"\u0185\5&\24\2\u0182\u0185\5T+\2\u0183\u0185\5R*\2\u0184\u0181\3\2\2\2"+
		"\u0184\u0182\3\2\2\2\u0184\u0183\3\2\2\2\u0185-\3\2\2\2\u0186\u0188\5"+
		",\27\2\u0187\u0186\3\2\2\2\u0188\u018b\3\2\2\2\u0189\u0187\3\2\2\2\u0189"+
		"\u018a\3\2\2\2\u018a/\3\2\2\2\u018b\u0189\3\2\2\2\u018c\u018d\7 \2\2\u018d"+
		"\u018e\5\u00b6\\\2\u018e\u0190\5<\37\2\u018f\u0191\7i\2\2\u0190\u018f"+
		"\3\2\2\2\u0190\u0191\3\2\2\2\u0191\u0192\3\2\2\2\u0192\u0193\7\4\2\2\u0193"+
		"\61\3\2\2\2\u0194\u0195\5\u00b6\\\2\u0195\63\3\2\2\2\u0196\u0197\7!\2"+
		"\2\u0197\u0198\5\u00b6\\\2\u0198\u019a\7\20\2\2\u0199\u019b\5\62\32\2"+
		"\u019a\u0199\3\2\2\2\u019a\u019b\3\2\2\2\u019b\u01a0\3\2\2\2\u019c\u019d"+
		"\7\21\2\2\u019d\u019f\5\62\32\2\u019e\u019c\3\2\2\2\u019f\u01a2\3\2\2"+
		"\2\u01a0\u019e\3\2\2\2\u01a0\u01a1\3\2\2\2\u01a1\u01a3\3\2\2\2\u01a2\u01a0"+
		"\3\2\2\2\u01a3\u01a4\7\22\2\2\u01a4\65\3\2\2\2\u01a5\u01ae\7\27\2\2\u01a6"+
		"\u01ab\5:\36\2\u01a7\u01a8\7\21\2\2\u01a8\u01aa\5:\36\2\u01a9\u01a7\3"+
		"\2\2\2\u01aa\u01ad\3\2\2\2\u01ab\u01a9\3\2\2\2\u01ab\u01ac\3\2\2\2\u01ac"+
		"\u01af\3\2\2\2\u01ad\u01ab\3\2\2\2\u01ae\u01a6\3\2\2\2\u01ae\u01af\3\2"+
		"\2\2\u01af\u01b0\3\2\2\2\u01b0\u01b1\7\30\2\2\u01b1\67\3\2\2\2\u01b2\u01b4"+
		"\5F$\2\u01b3\u01b5\5P)\2\u01b4\u01b3\3\2\2\2\u01b4\u01b5\3\2\2\2\u01b5"+
		"\u01b7\3\2\2\2\u01b6\u01b8\5\u00b6\\\2\u01b7\u01b6\3\2\2\2\u01b7\u01b8"+
		"\3\2\2\2\u01b89\3\2\2\2\u01b9\u01bb\5H%\2\u01ba\u01bc\5P)\2\u01bb\u01ba"+
		"\3\2\2\2\u01bb\u01bc\3\2\2\2\u01bc\u01be\3\2\2\2\u01bd\u01bf\5\u00b6\\"+
		"\2\u01be\u01bd\3\2\2\2\u01be\u01bf\3\2\2\2\u01bf;\3\2\2\2\u01c0\u01c9"+
		"\7\27\2\2\u01c1\u01c6\5> \2\u01c2\u01c3\7\21\2\2\u01c3\u01c5\5> \2\u01c4"+
		"\u01c2\3\2\2\2\u01c5\u01c8\3\2\2\2\u01c6\u01c4\3\2\2\2\u01c6\u01c7\3\2"+
		"\2\2\u01c7\u01ca\3\2\2\2\u01c8\u01c6\3\2\2\2\u01c9\u01c1\3\2\2\2\u01c9"+
		"\u01ca\3\2\2\2\u01ca\u01cb\3\2\2\2\u01cb\u01cc\7\30\2\2\u01cc=\3\2\2\2"+
		"\u01cd\u01cf\5F$\2\u01ce\u01d0\7n\2\2\u01cf\u01ce\3\2\2\2\u01cf\u01d0"+
		"\3\2\2\2\u01d0\u01d2\3\2\2\2\u01d1\u01d3\5\u00b6\\\2\u01d2\u01d1\3\2\2"+
		"\2\u01d2\u01d3\3\2\2\2\u01d3?\3\2\2\2\u01d4\u01dd\7\27\2\2\u01d5\u01da"+
		"\5B\"\2\u01d6\u01d7\7\21\2\2\u01d7\u01d9\5B\"\2\u01d8\u01d6\3\2\2\2\u01d9"+
		"\u01dc\3\2\2\2\u01da\u01d8\3\2\2\2\u01da\u01db\3\2\2\2\u01db\u01de\3\2"+
		"\2\2\u01dc\u01da\3\2\2\2\u01dd\u01d5\3\2\2\2\u01dd\u01de\3\2\2\2\u01de"+
		"\u01df\3\2\2\2\u01df\u01e0\7\30\2\2\u01e0A\3\2\2\2\u01e1\u01e3\5F$\2\u01e2"+
		"\u01e4\5P)\2\u01e3\u01e2\3\2\2\2\u01e3\u01e4\3\2\2\2\u01e4C\3\2\2\2\u01e5"+
		"\u01e7\5H%\2\u01e6\u01e8\5P)\2\u01e7\u01e6\3\2\2\2\u01e7\u01e8\3\2\2\2"+
		"\u01e8\u01e9\3\2\2\2\u01e9\u01ea\5\u00b6\\\2\u01eaE\3\2\2\2\u01eb\u01ec"+
		"\b$\1\2\u01ec\u01f3\5x=\2\u01ed\u01f3\5J&\2\u01ee\u01f3\5L\'\2\u01ef\u01f3"+
		"\5N(\2\u01f0\u01f1\7$\2\2\u01f1\u01f3\7p\2\2\u01f2\u01eb\3\2\2\2\u01f2"+
		"\u01ed\3\2\2\2\u01f2\u01ee\3\2\2\2\u01f2\u01ef\3\2\2\2\u01f2\u01f0\3\2"+
		"\2\2\u01f3\u01fc\3\2\2\2\u01f4\u01f5\f\5\2\2\u01f5\u01f7\7\"\2\2\u01f6"+
		"\u01f8\5z>\2\u01f7\u01f6\3\2\2\2\u01f7\u01f8\3\2\2\2\u01f8\u01f9\3\2\2"+
		"\2\u01f9\u01fb\7#\2\2\u01fa\u01f4\3\2\2\2\u01fb\u01fe\3\2\2\2\u01fc\u01fa"+
		"\3\2\2\2\u01fc\u01fd\3\2\2\2\u01fdG\3\2\2\2\u01fe\u01fc\3\2\2\2\u01ff"+
		"\u0200\b%\1\2\u0200\u0204\5x=\2\u0201\u0204\5J&\2\u0202\u0204\5L\'\2\u0203"+
		"\u01ff\3\2\2\2\u0203\u0201\3\2\2\2\u0203\u0202\3\2\2\2\u0204\u020d\3\2"+
		"\2\2\u0205\u0206\f\3\2\2\u0206\u0208\7\"\2\2\u0207\u0209\5|?\2\u0208\u0207"+
		"\3\2\2\2\u0208\u0209\3\2\2\2\u0209\u020a\3\2\2\2\u020a\u020c\7#\2\2\u020b"+
		"\u0205\3\2\2\2\u020c\u020f\3\2\2\2\u020d\u020b\3\2\2\2\u020d\u020e\3\2"+
		"\2\2\u020eI\3\2\2\2\u020f\u020d\3\2\2\2\u0210\u0215\5\u00b6\\\2\u0211"+
		"\u0212\7%\2\2\u0212\u0214\5\u00b6\\\2\u0213\u0211\3\2\2\2\u0214\u0217"+
		"\3\2\2\2\u0215\u0213\3\2\2\2\u0215\u0216\3\2\2\2\u0216K\3\2\2\2\u0217"+
		"\u0215\3\2\2\2\u0218\u0219\7&\2\2\u0219\u021a\7\27\2\2\u021a\u021b\5x"+
		"=\2\u021b\u021c\7\'\2\2\u021c\u021d\5H%\2\u021d\u021e\7\30\2\2\u021eM"+
		"\3\2\2\2\u021f\u0220\7\36\2\2\u0220\u0226\5@!\2\u0221\u0225\7o\2\2\u0222"+
		"\u0225\7m\2\2\u0223\u0225\5T+\2\u0224\u0221\3\2\2\2\u0224\u0222\3\2\2"+
		"\2\u0224\u0223\3\2\2\2\u0225\u0228\3\2\2\2\u0226\u0224\3\2\2\2\u0226\u0227"+
		"\3\2\2\2\u0227\u022b\3\2\2\2\u0228\u0226\3\2\2\2\u0229\u022a\7\37\2\2"+
		"\u022a\u022c\5@!\2\u022b\u0229\3\2\2\2\u022b\u022c\3\2\2\2\u022cO\3\2"+
		"\2\2\u022d\u022e\t\5\2\2\u022eQ\3\2\2\2\u022f\u0230\t\6\2\2\u0230S\3\2"+
		"\2\2\u0231\u0232\t\7\2\2\u0232U\3\2\2\2\u0233\u0237\7\20\2\2\u0234\u0236"+
		"\5X-\2\u0235\u0234\3\2\2\2\u0236\u0239\3\2\2\2\u0237\u0235\3\2\2\2\u0237"+
		"\u0238\3\2\2\2\u0238\u023a\3\2\2\2\u0239\u0237\3\2\2\2\u023a\u023b\7\22"+
		"\2\2\u023bW\3\2\2\2\u023c\u0249\5\\/\2\u023d\u0249\5^\60\2\u023e\u0249"+
		"\5b\62\2\u023f\u0249\5V,\2\u0240\u0249\5d\63\2\u0241\u0249\5f\64\2\u0242"+
		"\u0249\5h\65\2\u0243\u0249\5j\66\2\u0244\u0249\5l\67\2\u0245\u0249\5n"+
		"8\2\u0246\u0249\5p9\2\u0247\u0249\5`\61\2\u0248\u023c\3\2\2\2\u0248\u023d"+
		"\3\2\2\2\u0248\u023e\3\2\2\2\u0248\u023f\3\2\2\2\u0248\u0240\3\2\2\2\u0248"+
		"\u0241\3\2\2\2\u0248\u0242\3\2\2\2\u0248\u0243\3\2\2\2\u0248\u0244\3\2"+
		"\2\2\u0248\u0245\3\2\2\2\u0248\u0246\3\2\2\2\u0248\u0247\3\2\2\2\u0249"+
		"Y\3\2\2\2\u024a\u024b\5z>\2\u024b\u024c\7\4\2\2\u024c[\3\2\2\2\u024d\u024e"+
		"\7+\2\2\u024e\u024f\7\27\2\2\u024f\u0250\5z>\2\u0250\u0251\7\30\2\2\u0251"+
		"\u0254\5X-\2\u0252\u0253\7,\2\2\u0253\u0255\5X-\2\u0254\u0252\3\2\2\2"+
		"\u0254\u0255\3\2\2\2\u0255]\3\2\2\2\u0256\u0257\7-\2\2\u0257\u0258\7\27"+
		"\2\2\u0258\u0259\5z>\2\u0259\u025a\7\30\2\2\u025a\u025b\5X-\2\u025b_\3"+
		"\2\2\2\u025c\u025f\5r:\2\u025d\u025f\5Z.\2\u025e\u025c\3\2\2\2\u025e\u025d"+
		"\3\2\2\2\u025fa\3\2\2\2\u0260\u0261\7\32\2\2\u0261\u0264\7\27\2\2\u0262"+
		"\u0265\5`\61\2\u0263\u0265\7\4\2\2\u0264\u0262\3\2\2\2\u0264\u0263\3\2"+
		"\2\2\u0265\u0267\3\2\2\2\u0266\u0268\5z>\2\u0267\u0266\3\2\2\2\u0267\u0268"+
		"\3\2\2\2\u0268\u0269\3\2\2\2\u0269\u026b\7\4\2\2\u026a\u026c\5z>\2\u026b"+
		"\u026a\3\2\2\2\u026b\u026c\3\2\2\2\u026c\u026d\3\2\2\2\u026d\u026e\7\30"+
		"\2\2\u026e\u026f\5X-\2\u026fc\3\2\2\2\u0270\u0272\7.\2\2\u0271\u0273\7"+
		"v\2\2\u0272\u0271\3\2\2\2\u0272\u0273\3\2\2\2\u0273\u0274\3\2\2\2\u0274"+
		"\u0275\5\u008aF\2\u0275e\3\2\2\2\u0276\u0277\7/\2\2\u0277\u0278\5X-\2"+
		"\u0278\u0279\7-\2\2\u0279\u027a\7\27\2\2\u027a\u027b\5z>\2\u027b\u027c"+
		"\7\30\2\2\u027c\u027d\7\4\2\2\u027dg\3\2\2\2\u027e\u027f\7l\2\2\u027f"+
		"\u0280\7\4\2\2\u0280i\3\2\2\2\u0281\u0282\7j\2\2\u0282\u0283\7\4\2\2\u0283"+
		"k\3\2\2\2\u0284\u0286\7\60\2\2\u0285\u0287\5z>\2\u0286\u0285\3\2\2\2\u0286"+
		"\u0287\3\2\2\2\u0287\u0288\3\2\2\2\u0288\u0289\7\4\2\2\u0289m\3\2\2\2"+
		"\u028a\u028b\7\61\2\2\u028b\u028c\7\4\2\2\u028co\3\2\2\2\u028d\u028e\7"+
		"\62\2\2\u028e\u028f\5\u0088E\2\u028f\u0290\7\4\2\2\u0290q\3\2\2\2\u0291"+
		"\u0292\7\63\2\2\u0292\u0299\5v<\2\u0293\u0299\5D#\2\u0294\u0295\7\27\2"+
		"\2\u0295\u0296\5t;\2\u0296\u0297\7\30\2\2\u0297\u0299\3\2\2\2\u0298\u0291"+
		"\3\2\2\2\u0298\u0293\3\2\2\2\u0298\u0294\3\2\2\2\u0299\u029c\3\2\2\2\u029a"+
		"\u029b\7\13\2\2\u029b\u029d\5z>\2\u029c\u029a\3\2\2\2\u029c\u029d\3\2"+
		"\2\2\u029d\u029e\3\2\2\2\u029e\u029f\7\4\2\2\u029fs\3\2\2\2\u02a0\u02a2"+
		"\5D#\2\u02a1\u02a0\3\2\2\2\u02a1\u02a2\3\2\2\2\u02a2\u02a9\3\2\2\2\u02a3"+
		"\u02a5\7\21\2\2\u02a4\u02a6\5D#\2\u02a5\u02a4\3\2\2\2\u02a5\u02a6\3\2"+
		"\2\2\u02a6\u02a8\3\2\2\2\u02a7\u02a3\3\2\2\2\u02a8\u02ab\3\2\2\2\u02a9"+
		"\u02a7\3\2\2\2\u02a9\u02aa\3\2\2\2\u02aau\3\2\2\2\u02ab\u02a9\3\2\2\2"+
		"\u02ac\u02b3\7\27\2\2\u02ad\u02af\5\u00b6\\\2\u02ae\u02ad\3\2\2\2\u02ae"+
		"\u02af\3\2\2\2\u02af\u02b0\3\2\2\2\u02b0\u02b2\7\21\2\2\u02b1\u02ae\3"+
		"\2\2\2\u02b2\u02b5\3\2\2\2\u02b3\u02b1\3\2\2\2\u02b3\u02b4\3\2\2\2\u02b4"+
		"\u02b7\3\2\2\2\u02b5\u02b3\3\2\2\2\u02b6\u02b8\5\u00b6\\\2\u02b7\u02b6"+
		"\3\2\2\2\u02b7\u02b8\3\2\2\2\u02b8\u02b9\3\2\2\2\u02b9\u02ba\7\30\2\2"+
		"\u02baw\3\2\2\2\u02bb\u02bc\t\b\2\2\u02bcy\3\2\2\2\u02bd\u02be\b>\1\2"+
		"\u02be\u02bf\79\2\2\u02bf\u02d0\5F$\2\u02c0\u02c1\7\27\2\2\u02c1\u02c2"+
		"\5z>\2\u02c2\u02c3\7\30\2\2\u02c3\u02d0\3\2\2\2\u02c4\u02c5\t\t\2\2\u02c5"+
		"\u02d0\5z>\25\u02c6\u02c7\t\n\2\2\u02c7\u02d0\5z>\24\u02c8\u02c9\t\13"+
		"\2\2\u02c9\u02d0\5z>\23\u02ca\u02cb\7>\2\2\u02cb\u02d0\5z>\22\u02cc\u02cd"+
		"\7\6\2\2\u02cd\u02d0\5z>\21\u02ce\u02d0\5~@\2\u02cf\u02bd\3\2\2\2\u02cf"+
		"\u02c0\3\2\2\2\u02cf\u02c4\3\2\2\2\u02cf\u02c6\3\2\2\2\u02cf\u02c8\3\2"+
		"\2\2\u02cf\u02ca\3\2\2\2\u02cf\u02cc\3\2\2\2\u02cf\u02ce\3\2\2\2\u02d0"+
		"\u030c\3\2\2\2\u02d1\u02d2\f\20\2\2\u02d2\u02d3\7?\2\2\u02d3\u030b\5z"+
		">\21\u02d4\u02d5\f\17\2\2\u02d5\u02d6\t\f\2\2\u02d6\u030b\5z>\20\u02d7"+
		"\u02d8\f\16\2\2\u02d8\u02d9\t\n\2\2\u02d9\u030b\5z>\17\u02da\u02db\f\r"+
		"\2\2\u02db\u02dc\t\r\2\2\u02dc\u030b\5z>\16\u02dd\u02de\f\f\2\2\u02de"+
		"\u02df\7D\2\2\u02df\u030b\5z>\r\u02e0\u02e1\f\13\2\2\u02e1\u02e2\7\5\2"+
		"\2\u02e2\u030b\5z>\f\u02e3\u02e4\f\n\2\2\u02e4\u02e5\7E\2\2\u02e5\u030b"+
		"\5z>\13\u02e6\u02e7\f\t\2\2\u02e7\u02e8\t\16\2\2\u02e8\u030b\5z>\n\u02e9"+
		"\u02ea\f\b\2\2\u02ea\u02eb\t\17\2\2\u02eb\u030b\5z>\t\u02ec\u02ed\f\7"+
		"\2\2\u02ed\u02ee\7H\2\2\u02ee\u030b\5z>\b\u02ef\u02f0\f\6\2\2\u02f0\u02f1"+
		"\7I\2\2\u02f1\u030b\5z>\7\u02f2\u02f3\f\5\2\2\u02f3\u02f4\7J\2\2\u02f4"+
		"\u02f5\5z>\2\u02f5\u02f6\7K\2\2\u02f6\u02f7\5z>\6\u02f7\u030b\3\2\2\2"+
		"\u02f8\u02f9\f\4\2\2\u02f9\u02fa\t\20\2\2\u02fa\u030b\5z>\5\u02fb\u02fc"+
		"\f\33\2\2\u02fc\u030b\t\t\2\2\u02fd\u02fe\f\31\2\2\u02fe\u02ff\7\"\2\2"+
		"\u02ff\u0300\5z>\2\u0300\u0301\7#\2\2\u0301\u030b\3\2\2\2\u0302\u0303"+
		"\f\30\2\2\u0303\u0304\7\27\2\2\u0304\u0305\5\u0086D\2\u0305\u0306\7\30"+
		"\2\2\u0306\u030b\3\2\2\2\u0307\u0308\f\27\2\2\u0308\u0309\7%\2\2\u0309"+
		"\u030b\5\u00b6\\\2\u030a\u02d1\3\2\2\2\u030a\u02d4\3\2\2\2\u030a\u02d7"+
		"\3\2\2\2\u030a\u02da\3\2\2\2\u030a\u02dd\3\2\2\2\u030a\u02e0\3\2\2\2\u030a"+
		"\u02e3\3\2\2\2\u030a\u02e6\3\2\2\2\u030a\u02e9\3\2\2\2\u030a\u02ec\3\2"+
		"\2\2\u030a\u02ef\3\2\2\2\u030a\u02f2\3\2\2\2\u030a\u02f8\3\2\2\2\u030a"+
		"\u02fb\3\2\2\2\u030a\u02fd\3\2\2\2\u030a\u0302\3\2\2\2\u030a\u0307\3\2"+
		"\2\2\u030b\u030e\3\2\2\2\u030c\u030a\3\2\2\2\u030c\u030d\3\2\2\2\u030d"+
		"{\3\2\2\2\u030e\u030c\3\2\2\2\u030f\u0310\b?\1\2\u0310\u0311\t\n\2\2\u0311"+
		"\u0318\5|?\t\u0312\u0313\7\27\2\2\u0313\u0314\5|?\2\u0314\u0315\7\30\2"+
		"\2\u0315\u0318\3\2\2\2\u0316\u0318\5\u00b4[\2\u0317\u030f\3\2\2\2\u0317"+
		"\u0312\3\2\2\2\u0317\u0316\3\2\2\2\u0318\u0327\3\2\2\2\u0319\u031a\f\7"+
		"\2\2\u031a\u031b\7?\2\2\u031b\u0326\5|?\7\u031c\u031d\f\6\2\2\u031d\u031e"+
		"\t\f\2\2\u031e\u0326\5|?\7\u031f\u0320\f\5\2\2\u0320\u0321\t\n\2\2\u0321"+
		"\u0326\5|?\6\u0322\u0323\f\4\2\2\u0323\u0324\t\r\2\2\u0324\u0326\5|?\5"+
		"\u0325\u0319\3\2\2\2\u0325\u031c\3\2\2\2\u0325\u031f\3\2\2\2\u0325\u0322"+
		"\3\2\2\2\u0326\u0329\3\2\2\2\u0327\u0325\3\2\2\2\u0327\u0328\3\2\2\2\u0328"+
		"}\3\2\2\2\u0329\u0327\3\2\2\2\u032a\u0332\7c\2\2\u032b\u0332\5\u00b2Z"+
		"\2\u032c\u0332\7g\2\2\u032d\u0332\7v\2\2\u032e\u0332\5\u00b6\\\2\u032f"+
		"\u0332\5\u00aeX\2\u0330\u0332\5\u00b0Y\2\u0331\u032a\3\2\2\2\u0331\u032b"+
		"\3\2\2\2\u0331\u032c\3\2\2\2\u0331\u032d\3\2\2\2\u0331\u032e\3\2\2\2\u0331"+
		"\u032f\3\2\2\2\u0331\u0330\3\2\2\2\u0332\177\3\2\2\2\u0333\u0338\5z>\2"+
		"\u0334\u0335\7\21\2\2\u0335\u0337\5z>\2\u0336\u0334\3\2\2\2\u0337\u033a"+
		"\3\2\2\2\u0338\u0336\3\2\2\2\u0338\u0339\3\2\2\2\u0339\u0081\3\2\2\2\u033a"+
		"\u0338\3\2\2\2\u033b\u0340\5\u0084C\2\u033c\u033d\7\21\2\2\u033d\u033f"+
		"\5\u0084C\2\u033e\u033c\3\2\2\2\u033f\u0342\3\2\2\2\u0340\u033e\3\2\2"+
		"\2\u0340\u0341\3\2\2\2\u0341\u0344\3\2\2\2\u0342\u0340\3\2\2\2\u0343\u0345"+
		"\7\21\2\2\u0344\u0343\3\2\2\2\u0344\u0345\3\2\2\2\u0345\u0083\3\2\2\2"+
		"\u0346\u0347\5\u00b6\\\2\u0347\u0348\7K\2\2\u0348\u0349\5z>\2\u0349\u0085"+
		"\3\2\2\2\u034a\u034c\7\20\2\2\u034b\u034d\5\u0082B\2\u034c\u034b\3\2\2"+
		"\2\u034c\u034d\3\2\2\2\u034d\u034e\3\2\2\2\u034e\u0353\7\22\2\2\u034f"+
		"\u0351\5\u0080A\2\u0350\u034f\3\2\2\2\u0350\u0351\3\2\2\2\u0351\u0353"+
		"\3\2\2\2\u0352\u034a\3\2\2\2\u0352\u0350\3\2\2\2\u0353\u0087\3\2\2\2\u0354"+
		"\u0355\5z>\2\u0355\u0356\7\27\2\2\u0356\u0357\5\u0086D\2\u0357\u0358\7"+
		"\30\2\2\u0358\u0089\3\2\2\2\u0359\u035d\7\20\2\2\u035a\u035c\5\u008cG"+
		"\2\u035b\u035a\3\2\2\2\u035c\u035f\3\2\2\2\u035d\u035b\3\2\2\2\u035d\u035e"+
		"\3\2\2\2\u035e\u0360\3\2\2\2\u035f\u035d\3\2\2\2\u0360\u0361\7\22\2\2"+
		"\u0361\u008b\3\2\2\2\u0362\u0374\5\u00b6\\\2\u0363\u0374\5\u008aF\2\u0364"+
		"\u0374\5\u008eH\2\u0365\u0374\5\u0092J\2\u0366\u0374\5\u0094K\2\u0367"+
		"\u0374\5\u009aN\2\u0368\u0374\5\u009cO\2\u0369\u0374\5\u009eP\2\u036a"+
		"\u0374\5\u00a2R\2\u036b\u0374\5\u00a6T\2\u036c\u0374\5\u00a8U\2\u036d"+
		"\u0374\7j\2\2\u036e\u0374\7l\2\2\u036f\u0374\5\u00acW\2\u0370\u0374\5"+
		"\u00b2Z\2\u0371\u0374\7v\2\2\u0372\u0374\7g\2\2\u0373\u0362\3\2\2\2\u0373"+
		"\u0363\3\2\2\2\u0373\u0364\3\2\2\2\u0373\u0365\3\2\2\2\u0373\u0366\3\2"+
		"\2\2\u0373\u0367\3\2\2\2\u0373\u0368\3\2\2\2\u0373\u0369\3\2\2\2\u0373"+
		"\u036a\3\2\2\2\u0373\u036b\3\2\2\2\u0373\u036c\3\2\2\2\u0373\u036d\3\2"+
		"\2\2\u0373\u036e\3\2\2\2\u0373\u036f\3\2\2\2\u0373\u0370\3\2\2\2\u0373"+
		"\u0371\3\2\2\2\u0373\u0372\3\2\2\2\u0374\u008d\3\2\2\2\u0375\u0378\5\u0090"+
		"I\2\u0376\u0378\5\u00aaV\2\u0377\u0375\3\2\2\2\u0377\u0376\3\2\2\2\u0378"+
		"\u008f\3\2\2\2\u0379\u037e\7\60\2\2\u037a\u037e\7$\2\2\u037b\u037e\7\66"+
		"\2\2\u037c\u037e\5\u00b6\\\2\u037d\u0379\3\2\2\2\u037d\u037a\3\2\2\2\u037d"+
		"\u037b\3\2\2\2\u037d\u037c\3\2\2\2\u037e\u038b\3\2\2\2\u037f\u0381\7\27"+
		"\2\2\u0380\u0382\5\u008eH\2\u0381\u0380\3\2\2\2\u0381\u0382\3\2\2\2\u0382"+
		"\u0387\3\2\2\2\u0383\u0384\7\21\2\2\u0384\u0386\5\u008eH\2\u0385\u0383"+
		"\3\2\2\2\u0386\u0389\3\2\2\2\u0387\u0385\3\2\2\2\u0387\u0388\3\2\2\2\u0388"+
		"\u038a\3\2\2\2\u0389\u0387\3\2\2\2\u038a\u038c\7\30\2\2\u038b\u037f\3"+
		"\2\2\2\u038b\u038c\3\2\2\2\u038c\u0091\3\2\2\2\u038d\u038e\7V\2\2\u038e"+
		"\u0391\5\u0096L\2\u038f\u0390\7W\2\2\u0390\u0392\5\u008eH\2\u0391\u038f"+
		"\3\2\2\2\u0391\u0392\3\2\2\2\u0392\u0093\3\2\2\2\u0393\u0394\5\u0096L"+
		"\2\u0394\u0395\7W\2\2\u0395\u0396\5\u008eH\2\u0396\u0095\3\2\2\2\u0397"+
		"\u039d\5\u00b6\\\2\u0398\u0399\7\27\2\2\u0399\u039a\5\u0098M\2\u039a\u039b"+
		"\7\30\2\2\u039b\u039d\3\2\2\2\u039c\u0397\3\2\2\2\u039c\u0398\3\2\2\2"+
		"\u039d\u0097\3\2\2\2\u039e\u03a3\5\u00b6\\\2\u039f\u03a0\7\21\2\2\u03a0"+
		"\u03a2\5\u00b6\\\2\u03a1\u039f\3\2\2\2\u03a2\u03a5\3\2\2\2\u03a3\u03a1"+
		"\3\2\2\2\u03a3\u03a4\3\2\2\2\u03a4\u0099\3\2\2\2\u03a5\u03a3\3\2\2\2\u03a6"+
		"\u03a7\7X\2\2\u03a7\u03a8\5\u00b6\\\2\u03a8\u009b\3\2\2\2\u03a9\u03aa"+
		"\5\u00b6\\\2\u03aa\u03ab\7K\2\2\u03ab\u009d\3\2\2\2\u03ac\u03ad\7Y\2\2"+
		"\u03ad\u03b1\5\u008eH\2\u03ae\u03b0\5\u00a0Q\2\u03af\u03ae\3\2\2\2\u03b0"+
		"\u03b3\3\2\2\2\u03b1\u03af\3\2\2\2\u03b1\u03b2\3\2\2\2\u03b2\u009f\3\2"+
		"\2\2\u03b3\u03b1\3\2\2\2\u03b4\u03b5\7Z\2\2\u03b5\u03b6\5\u00aaV\2\u03b6"+
		"\u03b7\5\u008aF\2\u03b7\u03bb\3\2\2\2\u03b8\u03b9\7[\2\2\u03b9\u03bb\5"+
		"\u008aF\2\u03ba\u03b4\3\2\2\2\u03ba\u03b8\3\2\2\2\u03bb\u00a1\3\2\2\2"+
		"\u03bc\u03bd\7\36\2\2\u03bd\u03be\5\u00b6\\\2\u03be\u03c0\7\27\2\2\u03bf"+
		"\u03c1\5\u0098M\2\u03c0\u03bf\3\2\2\2\u03c0\u03c1\3\2\2\2\u03c1\u03c2"+
		"\3\2\2\2\u03c2\u03c4\7\30\2\2\u03c3\u03c5\5\u00a4S\2\u03c4\u03c3\3\2\2"+
		"\2\u03c4\u03c5\3\2\2\2\u03c5\u03c6\3\2\2\2\u03c6\u03c7\5\u008aF\2\u03c7"+
		"\u00a3\3\2\2\2\u03c8\u03c9\7\\\2\2\u03c9\u03ca\5\u0098M\2\u03ca\u00a5"+
		"\3\2\2\2\u03cb\u03ce\7\32\2\2\u03cc\u03cf\5\u008aF\2\u03cd\u03cf\5\u008e"+
		"H\2\u03ce\u03cc\3\2\2\2\u03ce\u03cd\3\2\2\2\u03cf\u03d0\3\2\2\2\u03d0"+
		"\u03d3\5\u008eH\2\u03d1\u03d4\5\u008aF\2\u03d2\u03d4\5\u008eH\2\u03d3"+
		"\u03d1\3\2\2\2\u03d3\u03d2\3\2\2\2\u03d4\u03d5\3\2\2\2\u03d5\u03d6\5\u008a"+
		"F\2\u03d6\u00a7\3\2\2\2\u03d7\u03d8\7+\2\2\u03d8\u03d9\5\u008eH\2\u03d9"+
		"\u03da\5\u008aF\2\u03da\u00a9\3\2\2\2\u03db\u03dc\t\21\2\2\u03dc\u00ab"+
		"\3\2\2\2\u03dd\u03de\7.\2\2\u03de\u03df\5\u00b6\\\2\u03df\u03e0\5\u008a"+
		"F\2\u03e0\u00ad\3\2\2\2\u03e1\u03e3\7\27\2\2\u03e2\u03e4\5z>\2\u03e3\u03e2"+
		"\3\2\2\2\u03e3\u03e4\3\2\2\2\u03e4\u03eb\3\2\2\2\u03e5\u03e7\7\21\2\2"+
		"\u03e6\u03e8\5z>\2\u03e7\u03e6\3\2\2\2\u03e7\u03e8\3\2\2\2\u03e8\u03ea"+
		"\3\2\2\2\u03e9\u03e5\3\2\2\2\u03ea\u03ed\3\2\2\2\u03eb\u03e9\3\2\2\2\u03eb"+
		"\u03ec\3\2\2\2\u03ec\u03ee\3\2\2\2\u03ed\u03eb\3\2\2\2\u03ee\u03fc\7\30"+
		"\2\2\u03ef\u03f8\7\"\2\2\u03f0\u03f5\5z>\2\u03f1\u03f2\7\21\2\2\u03f2"+
		"\u03f4\5z>\2\u03f3\u03f1\3\2\2\2\u03f4\u03f7\3\2\2\2\u03f5\u03f3\3\2\2"+
		"\2\u03f5\u03f6\3\2\2\2\u03f6\u03f9\3\2\2\2\u03f7\u03f5\3\2\2\2\u03f8\u03f0"+
		"\3\2\2\2\u03f8\u03f9\3\2\2\2\u03f9\u03fa\3\2\2\2\u03fa\u03fc\7#\2\2\u03fb"+
		"\u03e1\3\2\2\2\u03fb\u03ef\3\2\2\2\u03fc\u00af\3\2\2\2\u03fd\u03fe\5x"+
		"=\2\u03fe\u00b1\3\2\2\2\u03ff\u0401\5\u00b4[\2\u0400\u0402\7f\2\2\u0401"+
		"\u0400\3\2\2\2\u0401\u0402\3\2\2\2\u0402\u00b3\3\2\2\2\u0403\u0406\7d"+
		"\2\2\u0404\u0406\7e\2\2\u0405\u0403\3\2\2\2\u0405\u0404\3\2\2\2\u0406"+
		"\u00b5\3\2\2\2\u0407\u0408\t\22\2\2\u0408\u00b7\3\2\2\2s\u00bb\u00bd\u00cb"+
		"\u00cf\u00d4\u00db\u00e1\u00e7\u00eb\u00f7\u00ff\u0109\u010c\u0112\u011e"+
		"\u0123\u012d\u0134\u0139\u013d\u0141\u0148\u0156\u0159\u0165\u016c\u016f"+
		"\u0173\u0178\u017c\u0184\u0189\u0190\u019a\u01a0\u01ab\u01ae\u01b4\u01b7"+
		"\u01bb\u01be\u01c6\u01c9\u01cf\u01d2\u01da\u01dd\u01e3\u01e7\u01f2\u01f7"+
		"\u01fc\u0203\u0208\u020d\u0215\u0224\u0226\u022b\u0237\u0248\u0254\u025e"+
		"\u0264\u0267\u026b\u0272\u0286\u0298\u029c\u02a1\u02a5\u02a9\u02ae\u02b3"+
		"\u02b7\u02cf\u030a\u030c\u0317\u0325\u0327\u0331\u0338\u0340\u0344\u034c"+
		"\u0350\u0352\u035d\u0373\u0377\u037d\u0381\u0387\u038b\u0391\u039c\u03a3"+
		"\u03b1\u03ba\u03c0\u03c4\u03ce\u03d3\u03e3\u03e7\u03eb\u03f5\u03f8\u03fb"+
		"\u0401\u0405";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}