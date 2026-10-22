// Performance tests assert their own time bounds; mocha's 2 s per-test default is too tight for CI browsers.
config.set({
    client: {
        mocha: {
            timeout: 30000,
        },
    },
});
