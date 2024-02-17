<?php

// FILE app/Common/ImportExport/ImportExport.php
function gettype_switch($value) {

    $value = unserialize($value);

    switch (gettype($value)) {
        case 'boolean':
                return (bool) $value;
        case 'string':
                return $value;
        case 'integer':
                return intval( $value );
        case 'double':
                return floatval( $value );
        case 'array':
                $sanitized = [];
                foreach ((array) $value as $k => $v) {
                        $sanitized[ $k ] = gettype_switch($v);
                }
                return $sanitized;
        default:
                return '';
    }
}

?>
