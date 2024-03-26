<?php

require_once __DIR__ . '/../vendor/autoload.php';

use PhpParser\Error;
use PhpParser\Node;
use PhpParser\NodeDumper;
use PhpParser\NodeTraverser;
use PhpParser\NodeVisitor\NameResolver;
use PhpParser\NodeVisitor\ParentConnectingVisitor;
use PhpParser\NodeVisitorAbstract;
use PhpParser\ParserFactory;

use PHPStan\PhpDocParser\Ast\PhpDoc\ParamTagValueNode;
use PHPStan\PhpDocParser\Ast\PhpDoc\PhpDocNode;
use PHPStan\PhpDocParser\Ast\Type\IdentifierTypeNode;
use PHPStan\PhpDocParser\Ast\Type\UnionTypeNode;
use PHPStan\PhpDocParser\Lexer\Lexer;
use PHPStan\PhpDocParser\Parser\ConstExprParser;
use PHPStan\PhpDocParser\Parser\PhpDocParser;
use PHPStan\PhpDocParser\Parser\TokenIterator;
use PHPStan\PhpDocParser\Parser\TypeParser;

/**
 * Represents the type information for a function declaration.
 */
class FunctionType {

    public string $name;

    /** @var array<array<string>> $params */
    public array $params;

    /** @var array<string> $returns */
    public array $returns;

    /**
     * @parameter string $name: function name
     * @parameter array<ParameterTag> $paramTags: list of parameter tags from
     *            PhpDocParser, with one entry per function parameter.
     * @parameter array<ReturnTag> $returnTags: return tags from PhpDocParser,
     *            but should only have one entry.
     */
    public function __construct(string $name, array $paramTags, array $returnTags) {
        $this->name = $name;

        /* Parse the list of returnTags to turn it into a list of string names
         * of return types. We expect this list to always have one element in
         * it. When the function has multiple return types
         * (e.g., @return ClassA | ClassB ), the list contains one element of
         * UnionTypeNode, which wraps nodes of type ClassA and ClassB. */
        $this->returns = array();
        foreach ($returnTags as $returnTag) {
            if ($returnTag->type instanceof UnionTypeNode) {
                /* Unwrap the UnionTypeNode to get the actual return types. */
                $this->returns = array_map(function($tag) { return $tag->name; }, $returnTag->type->types);
            } else {
                /* Otherwise, we can just fetch the type name from the tag by
                 * casting it to a string. */
                $this->returns = array_map(function($tag) { return "{$tag}"; }, $returnTags);
            }
        }

        /* Treat the params list similarly as the returns list, but now there
         * can be multiple elements in the params list (one for each parameter
         * of a function), and each parameter can have multiple types, which
         * are also represented as UnionTypeNodes. The params therefore needs
         * to be a list of lists, where each sub-list corresponds to one
         * function parameter. */
        $this->params = array();
        foreach ($paramTags as $paramTag) {
            if ($paramTag->type instanceof UnionTypeNode) {
                array_push(
                    $this->params,
                    array_map(
                        function($tag) { return $tag->name; },
                        $paramTag->type->types));
            } else {
                array_push($this->params, array("{$paramTag->type}"));
            }
        }
    }


    public function __toString() {
        $returns_string = implode(',', $this->returns);
        $params_string = "";
        foreach($this->params as $param_entry) {
            $params_string .= implode(',', $param_entry);
            $params_string .= "; ";
        }
        return $this->name . "; " . $returns_string . "; " . $params_string;
    }

    /* This is a cleaner format, but Joern does not parse this format yet.
        TODO: Update Joern to take as input a more natural PHP function signature,
            and then use this (slightly modified) implementation.
    public function __toString() {
        $params_string = implode(', ', array_map(function($param) {
                return "{$param->parameterName}: {$param->type}";
            }, $this->params));
        $returns_string = implode('|', array_map(function($return) {
                return "{$return->type}";
            }, $this->returns));
        $repr_string = $this->name . "(" . $params_string . "): " . $returns_string;
        return $repr_string;
    }
    */

}

/**
 * Visits each node in an AST. For all functions and class methods, the visitor
 * will check if a docblock comment exists for the node, and if so, create a
 * FunctionType object for it.
 *
 * Internally, this calls the parse_docblock function, which uses the PHPStan
 * PHPDoc parser to parse the found docblocks.
 *
 * All found function types will be in the $function_types array after
 * processing.
 */
class ExtractDocblockVisitor extends NodeVisitorAbstract {

    /**
     * @var array<FunctionType> function_types
     */
    public $function_types = [];

    public function leaveNode(Node $node) {

        if ($node->getDocComment() && $node instanceof Node\Stmt\Function_) {

            $this->function_types[] = parse_docblock(
                $node->namespacedName->toString(),
                $node->getDocComment());

        } elseif ($node->getDocComment() && $node instanceof Node\Stmt\ClassMethod) {

            $parent = $node->getAttribute('parent');
            if ($parent instanceof Node\Stmt\Class_) {
                $sep = $node->isStatic() ? "::" : "->";
                $name = $parent->namespacedName . $sep . $node->name;
            } else {
                $name = $node->name;
            }
            $this->function_types[] = parse_docblock($name, $node->getDocComment());
        }
    }

}

/**
 * Uses nikic/php-parser to parse the string contents of a PHP file into an
 * AST.
 *
 * @param string $code
 * @return Node\Stmt[] | null
 */
function get_ast(string $code): ?array {
    $parser = (new ParserFactory())->create(ParserFactory::PREFER_PHP7);
    try {
        $ast = $parser->parse($code);
    } catch (Error $error) {
        echo "Parse error: {$error->getMessage()}\n";
        return null;
    }

    return $ast;
}

/**
 * Uses the PHPStan PHPDoc Parser to parse docblocks and turn them into
 * FunctionType objects.
 *
 * @param string $fn_name
 * @param string $docblock
 * @return FunctionType
 */
function parse_docblock(string $fn_name, string $docblock): FunctionType {

    $lexer = new Lexer();
    $constExprParser = new ConstExprParser();
    $typeParser = new TypeParser($constExprParser);
    $phpDocParser = new PhpDocParser($typeParser, $constExprParser);

    $tokens = new TokenIterator($lexer->tokenize($docblock));
    $phpDocNode = $phpDocParser->parse($tokens);
    $paramTags = $phpDocNode->getParamTagValues();
    $returnTags = $phpDocNode->getReturnTagValues();

    return new FunctionType($fn_name, $paramTags, $returnTags);
}

if ($argc < 2) {
    echo "USAGE: {$argv[0]} <filename.php>" . "\n";
    return;
}
$filename = $argv[1];

$parentResolver = new NodeTraverser;
$parentResolver->addvisitor(new ParentConnectingVisitor);

$nameResolver = new NodeTraverser;
$nameResolver->addvisitor(new NameResolver);

$code = file_get_contents($filename);
$ast = get_ast($code);
if (!$ast) {
    return -1;
}

// Traverse twice, first to resolve parent nodes and then to resolve names.
$ast = $parentResolver->traverse($ast);
$ast = $nameResolver->traverse($ast);

// Traverse again, this time with the ability to get parent nodes
$docblockExtractor = new NodeTraverser;
$docblockExtractorVisitor = new ExtractDocblockVisitor;
$docblockExtractor->addVisitor($docblockExtractorVisitor);
$docblockExtractor->traverse($ast);

foreach($docblockExtractorVisitor->function_types as $fn_type) {
    echo $fn_type . "\n";
}
