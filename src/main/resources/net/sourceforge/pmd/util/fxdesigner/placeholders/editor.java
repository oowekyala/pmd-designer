class Foo {

  /*
    This is a custom designer crafted to diff between the prototype Java grammar
    for 7.0.0 and the current grammar. You can see both trees to the right.
    Node selection should be in sync. Also there's a javadoc viewer on the left.
    (also there's a nicer XPath export wizard but I only include it
     because that branch fixes some critical performance issues)

    The 6.0.x tree can be color-coded to highlight differences. Key:
    * Red: removed node. Turned into an interface in the code most of the time.
    * Yellow: proposed removal, to be thought about

    Those are all nodes whose presence doesn't add anything to the tree and
    which in all cases were worked around in rules. Using interfaces declutters
    the tree, removes inconsistencies, and makes it look like more of an AST
    than a *parse tree*.

    I ran some benchmarks to compare the old parser to this one.
    * Parsing performance is equivalent
    * ASTs are on average about 60% as big (they're smaller)
    * Since the AST is more compact, the runtime of a full visitor traversal
      is decreased on average by 12%

  */


    /*
       EXPRESSIONS
       * Full changelog at the bottom
     */

    {
        // Literals

        long l = 0 + 0l;   // NumericLiteral
        double d = l * 0d; // VariableReference
        String f = "  ";   // StringLiteral
        char c = 'c';      // CharLiteral
        Object o = null;   // NullLiteral
        boolean b = true;  // BooleanLiteral
        Class<String> klass = String.class;     // ClassLiteral

        // ParenthesizedExpression/NullLiteral
        // easier than Expression/PrimaryExpression/PrimaryPrefix/Expression/PrimaryExpression/PrimaryPrefix/Literal/NullLiteral
        Object k = (null);


        // FieldAccess with a ThisExpression as LHS
        // Also, AssignmentExpression
        this.myName = "foo";
        // ThisExpression
        Object me = this;


        // ArrayAllocation and ConstructorCall replace AllocationExpression

        // ArrayAllocation
        int[] is = new int[] {1, 2};
        is = new int[2];

        // ConstructorCall
        me = new Foo();
        // qualified constructor call
        me = me.new Inner().foo();
        // anonymous class
        me = me.new Inner() {
            // qualified this
            String outerName = Foo.this.myName;
        };

        // notice that "me" in the assignments above is not syntactically
        // ambiguous, must be a variable because we assign to it

        // SuperExpression
        // "super" is exactly symmetric to "this"
        super.fooo();
        Any.super.bar();

        // array type, array initializer
        // look at how many levels are removed from the array initializer
        int[][] js = {{1, 2}, {3}, is, null};

        // ArrayAccess
        js[1] = js[0 + 0]; // notice "js" is not syntactically ambiguous

        // Lambda
        Consumer<String> fun = s -> {
            // AmbiguousName
            // more explanations are given in the changelog at the bottom
            System.out.println(s.length()); // s is unambiguous bc the parameter is in scope
        };

        // method reference, ambiguous
        foo(System.out::println);
        // here "String" is unambiguously a type name (bc of "new")
        foo(String::new);
        // But here, "java.lang" is ambiguous between package or type name
        foo(java.lang.String::new);

        // ShiftExpression
        // no more RUNSIGNEDSHIFT/RSIGNEDSHIFT
        int shifted = l >> 2;
        shifted = shifted >>> 3;

        // UnaryExpression
        // UnaryExpressionNotPlusMinus is removed, it was an accident of the grammar
        boolean not = !!true;
        shifted = -shifted / ~shifted;
        // should we merge Pre[In|De]crementExpression into UnaryExpression?
        // they have the same precedence, and there's no counterpart for
        // PostIncrement & PostDecrement, which makes it look inconsistent
        shifted = --shifted + shifted--;
    }


    /*
       ANNOTATIONS
       * Turn Annotation into an interface
       * Remove Name nodes, useless in all cases
       * Remove MemberValuePairs. MemberValuePair may only occur in
         NormalAnnotation so that node added no information.
       * TBH we could also merge NormalAnnotation, MarkerAnnotation and SingleMemberAnnotation
         into a single node. It's not very useful to separate them that way. We'd have
         a single node "Annotation", with the following grammar:
         Annotation        := "@" <Name #void> [ AnnotationMembers ]
         AnnotationMembers := "(" (Expression | (MemberValuePair)*) ")"
           So eg
           @Foo              ~> Annotation
           @Foo()            ~> Annotation { AnnotationMembers }
           @Foo("foo")       ~> Annotation { AnnotationMembers { StringLiteral } }
           @Foo(value="foo") ~> Annotation { AnnotationMembers { MemberValuePair { StringLiteral } } }

         Wdyt?
     */

    @SomeAnnot()
    @SomeAnnot(name = "foo", arr = {@B})
    @Single("foo")
    @java.lang.Override
    String myName = "name";



    /*
      TYPES
      * ClassOrInterfaceTypes are now left-recursive. They use AmbiguousName
        too, because some segments need to be disambiguated between package
        or type name
      * TypeArgument, WildcardBound and TypeBound are removed, replaced with WildcardType
        and IntersectionType
    */

    // Wildcard types
    List<?> abc;
    List<? extends B> abc;
    List<? super B> abc;
    // Array types
    List<String>[] sss1;
    List<String> @Foo [] sss2;


    // Class or interface types

    // the qualifier is ambiguous
    // pretty easy to classify though (I didn't bother with @SemanticCheck here)
    java.util.List<String> sss3;
    java.util.Map.Entry sss5;
    java.util.Map<String, String>.Entry<String, String> sss6;
    // this parses now, but not with the old parser (#1367)
    // java.util.Map.@Foo Entry sss5;

    // Intersection types

    // This type parameter is an IntersectionType
    <T extends Foo & Bar> void foo(T t) {
        Object me = null;

        // Intersection types in casts
        me = (@B Foo) me;
        me = (@B Foo & Bar) me;
        me = (Foo & Bar) me;
    }
}


    /*

Changelog for the Expression grammar
  * Make ASTVariableInitializer, ASTExpression, ASTPrimaryExpression, ASTLiteral interfaces
  * Introduce new node types:
    * ASTClassLiteral, ASTNumericLiteral, ASTStringLiteral, ASTCharLiteral
      * those divide the work of ASTLiteral, they implement it along with preexisting ASTBooleanLiteral, ASTNullLiteral
    * ASTFieldAccess
      * Only when it has a LHS, e.g. "a.b"
      * Only pushed when we're certain this cannot be a FQCN or type member
    * ASTVariableReference
      * Unqualified reference to a variable
    * ASTAmbiguousName
      * For those names that are *syntactically* ambiguous
      * Most of them aren't semantically ambiguous, even with no auxclasspath, meaning
        they can be rewritten to a more meaningful node (a VariableReference or FieldAccess).
        The attribute AmbiguousName/@SemanticCheck makes a (very simple) disambiguation
        using the current (full of holes) symbol table and other easy to grab stuff:
        * If there's a variable in scope with the name, then it's classified as EXPR
        * Otherwise, if there's a static import with the given name, then it's classified as EXPR
        * Otherwise, if there's an import with the given name, it's classified as TYPE
        * Otherwise, it's classified as AMBIGUOUS
        * Many scenarios where there's no ambiguity without auxclasspath are not covered
        * With an auxclasspath and a proper symbol table, we could reclassify them all

        This is a very simple prototype, any real implementation would use the more precise
        JLS rules and some more heuristics.
        I found that nevertheless, around 90% of syntactically ambiguous names are
        semantically unambiguous using those simple rules (that was observed on several
        large codebases). This is without any auxclasspath support.

    * ASTMethodCall
      * Doesn't use ASTArguments anymore, ASTArgumentsList is enough
    * ASTParenthesizedExpression
    * ASTArrayAccess
    * ASTAssignmentExpression
    * ASTArrayAllocation, ASTConstructorCall
      * those replace ASTAllocationExpression
    * ASTThisExpression, ASTSuperExpression
      * those enclose their qualifier if any
  * Remove unnecessary node types:
    * ASTPrimarySuffix and ASTPrimaryPrefix
      * they're productions, not nodes
    * ASTUnaryExpressionNotPlusMinus
      * Merged into ASTUnaryExpression
    * ASTAssignmentOperator
      * made obsolete by ASTAssignmentExpression
    * ASTArguments
      * To remove it, ASTExplicitConstructorInvocation must be changed too
    * ASTVariableInitializer
      * Is superseded by ASTExpression, through making ASTArrayInitializer implement ASTExpression.
        This is only logical, they're expressions according to JLS. See deprecation note.
    * ASTRSIGNEDSHIFT, ASTRUNSIGNEDSHIFT
    * ASTAllocationExpression
      * The concrete node is replaced by ASTConstructorCall and ASTArrayAllocation, though we could make it an interface instead of removing it
*/
