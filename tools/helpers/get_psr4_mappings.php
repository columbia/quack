<?php

function get_psr4_mappings($mappings) {
    return require $mappings;
}

if ($argc != 2) {
    echo "Usage: $argv[0] </path/to/psr4/mappings>";
    exit(-1);
}

$mappings_path = $argv[1];
$mappings = get_psr4_mappings($mappings_path);
if (count($mappings) == 0){
    print("{}");
} else {
    print(json_encode($mappings));
}

?>
