class Foo {

  /*
    This is a custom designer crafted to diff between the prototype Java grammar
    for 7.0.0 and the current grammar. You can see both trees to the right.
    Node selection should be in sync. Also there's a javadoc viewer on the left.

    The 6.0.x tree can be color-coded to highlight differences. Key:
    * Red: removed node. Turned into an interface in the code most of the time.
    * Yellow: proposed removal, to be thought about

  */


    /*
       EXPRESSIONS
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

        // NullLiteral[@Parenthesized = true()]
        // easier than Expression/PrimaryExpression/PrimaryPrefix/Expression/PrimaryExpression/PrimaryPrefix/Literal/NullLiteral
        Object k = (null);


        // AdditiveExpression implements #1661
        // A new level is pushed every time the operator changes
        // So this is flat:
        int q = l + b + 0 + c;
        // But this has two levels:
        q = l + b - 0 - c;

        // Same for MultiplicativeExpression
        q = 2 * 3 * b / 1;

        // Notice that the tree is the same, even with parentheses
        // Semantically equivalent expressions that differ only by parentheses
        // are parsed the same
        q = (2 * 3) * b / 1;
        // Here, the parens are necessary, yet don't introduce a new level of nesting
        // Check out the @Parenthesized attribute, and @ParenthesisDepth
        q = 1 * (2 + 3);
        // Another equivalent expr, with different @ParenthesisDepth attrs
        q = (1 * ((2 + 3)));


        // ArrayAllocation and ConstructorCall replace AllocationExpression

        // ArrayAllocation
        int[] is = new int[] {1, 2};
        is = new @A int[2];

        // ConstructorCall
        me = new Foo();
        // qualified constructor call
        me = me.new Inner();
        // anonymous class
        me = me.new Inner() {
            // qualified this
            String outerName = Foo.this.myName;
        };

        // notice that "me" in the assignments above is not syntactically
        // ambiguous, must be a variable because we assign to it

        // SuperExpression, MethodCall
        // "super" is exactly symmetric to "this"
        super.fooo();
        Any.super.bar();

        // FieldAccess with a ThisExpression as LHS
        // Also, AssignmentExpression
        this.myName = "foo";
        // ThisExpression
        Object me = this;


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
         into a single node. It's not very useful to separate them that way. We could have
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
    List<? extends B> abc2;
    List<? super B> abc3;
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
    // Notice TypeBound is removed
    <T extends Foo & Bar> void foo(T t) {
        Object me = null;

        // Intersection types in casts
        me = (@B Foo) me;
        me = (@B Foo & Bar) me;
        me = (Foo & Bar) me;
    }
}
