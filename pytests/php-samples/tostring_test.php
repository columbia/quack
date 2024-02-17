<?php

// FILE app/Common/ImportExport/RankMath/PostMeta.php

class HasToString
{
    public function __toString() {
        return 'A string';
    }

    public function foo() {
        return 'foo';
    }
}

class Dummy
{
    public function foo() {
        return 'foo';
    }
}

function test_tostring($value, $meta) {
    $value = unserialize($value);
    if (!empty($value)) {
        foreach($value as $robotsName) {
            // Can only be HasToString
            $meta["robots_$robotsName"] = true;
        }
    }
}

?>
