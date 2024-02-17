<?php

    class Stream {
        public function clear() {
            $this->close();
        }
        public function close() {
            $this->handle->close();
        }
    }

    class TempFile extends Stream {
        public function save() {
            $tmpfile = tempnam("/tmp", "XYZ_");
            $data = file_get_contents($this->filename);
            file_put_contents($tmpfile, $data); // Sink
        }
        public function close() {
            print "deleting file: " . $this->filename . "\n";
            unlink($this->filename);    // Sink
        }
    }

?>
