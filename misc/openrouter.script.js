(async function() {
    const processedLinks = new Set();

    while (true) {
        const items = document.querySelectorAll("body > main > div.mx-auto.flex.h-full.min-h-screen.w-full.max-w-screen-4xl > section > div.relative.w-full > div > ul > li:nth-child(n)");
        let newItemsFound = false;


        for (const item of items) {
            const linkElement = item.querySelector("a");
            if (linkElement) {
                let href = linkElement.getAttribute("href");
                if (href.startsWith("/")) {
                    href = href.slice(1);
                }
                if (!processedLinks.has(href)) {
                    processedLinks.add(href);
                    //console.log(href);
                    newItemsFound = true;
                }
            }
        }

        if (!newItemsFound) {
            console.log("No new items found. Exiting: ", processedLinks);
            break;
        }

        const lastItem = items[items.length - 1];
        if (lastItem) {
            lastItem.scrollIntoView({ behavior: "smooth" });
            await new Promise(resolve => setTimeout(resolve, 2000)); // Wait for content to load
        } else {
            console.log("No items found. Exiting: \n", processedLinks);
            break;
        }
    }
})();

/*

deepseek/deepseek-chat-v3-0324:free
deepseek/deepseek-r1-0528:free
qwen/qwen3-coder:free
deepseek/deepseek-r1:free
z-ai/glm-4.5-air:free
tngtech/deepseek-r1t2-chimera:free
moonshotai/kimi-k2:free
tngtech/deepseek-r1t-chimera:free
google/gemini-2.0-flash-exp:free
qwen/qwen3-235b-a22b:free
openai/gpt-oss-20b:free
meta-llama/llama-3.3-70b-instruct:free
microsoft/mai-ds-r1:free
deepseek/deepseek-r1-0528-qwen3-8b:free
mistralai/mistral-small-3.2-24b-instruct:free
qwen/qwen2.5-vl-72b-instruct:free
mistralai/mistral-small-3.1-24b-instruct:free
cognitivecomputations/dolphin-mistral-24b-venice-edition:free
mistralai/mistral-nemo:free
qwen/qwen3-14b:free
qwen/qwen-2.5-coder-32b-instruct:free
google/gemma-3-27b-it:free
tencent/hunyuan-a13b-instruct:free
moonshotai/kimi-dev-72b:free
deepseek/deepseek-r1-distill-llama-70b:free
qwen/qwen3-30b-a3b:free
agentica-org/deepcoder-14b-preview:free
mistralai/mistral-7b-instruct:free
meta-llama/llama-3.1-405b-instruct:free
qwen/qwen-2.5-72b-instruct:free
mistralai/devstral-small-2505:free
moonshotai/kimi-vl-a3b-thinking:free
qwen/qwen2.5-vl-32b-instruct:free
qwen/qwen3-8b:free
nousresearch/deephermes-3-llama-3-8b-preview:free
qwen/qwq-32b:free
meta-llama/llama-3.2-11b-vision-instruct:free
cognitivecomputations/dolphin3.0-mistral-24b:free
qwen/qwen3-4b:free
google/gemma-3n-e2b-it:free
shisa-ai/shisa-v2-llama3.3-70b:free
google/gemma-3-12b-it:free
arliai/qwq-32b-arliai-rpr-v1:free
google/gemma-2-9b-it:free
mistralai/mistral-small-24b-instruct-2501:free
cognitivecomputations/dolphin3.0-r1-mistral-24b:free
meta-llama/llama-3.2-3b-instruct:free
google/gemma-3n-e4b-it:free
featherless/qwerky-72b:free
google/gemma-3-4b-it:free
sarvamai/sarvam-m:free
nvidia/llama-3.1-nemotron-ultra-253b-v1:free
rekaai/reka-flash-3:free
deepseek/deepseek-r1-distill-qwen-14b:free

*/