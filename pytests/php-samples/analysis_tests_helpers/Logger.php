<?php

    class Logger {
        /*
         * If this was a __destruct or __sleep magic method, the attacker would need
         * to create an object of the correct class (i.e., Logger) during deserialization,
         * else subsequent calls to methods of an object of the wrong class would fail
         * (e.g., the call to the hello() method after deserialization in this example
         * would fail on an instance of Logger) and throw a runtime error before __destruct is called. Since
         * this is a __wakeup function instead, it will get called while the object is being deserialized,
         * before any methods are called on it, and the attack will succeed even if the instance
         * of the object is of the wrong class.
        */
        public function __destruct() { // Magic method
        /* public function __wakeup() { // Magic method */
            if ($this->logtype == "TEMPORARY") {
                $this->log->clear();
            } else {
                $this->log->save();
            }
        }
    }

?>
