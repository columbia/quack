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

    /** @var array<ParameterTag> $params */
    public array $params;

    /** @var array<ReturnTag> $returns */
    public array $returns;

    public function __construct(string $name, array $paramTags, array $returnTags) {
        $this->name = $name;
        $this->params = $paramTags;
        $this->returns = $returnTags;
    }

    public function __toString() {
        $returns_string = implode(',', array_map(function($return) {
            return "{$return->type}";
        }, $this->returns));
        $params_string = implode('; ', array_map(function($param) {
            return "{$param->type}";
        }, $this->params));
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
